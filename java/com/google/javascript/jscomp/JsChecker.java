/*
 * Copyright 2016 The Closure Rules Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.lint.CheckJSDocStyle;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Program for incrementally checking JavaScript code.
 *
 * <p>This program is invoked once for each {@code closure_js_library} rule. It validates JS files
 * for most of the really bad syntax errors, but doesn't do robust type checking because it can't
 * take the full program into consideration. This program also performs linting, which is something
 * that does not happen on {@code closure_js_binary} rules.
 *
 * <p>But most importantly, this program does strict dependency checking. It is able to verify that
 * required namespaces are provided by direct dependencies rather than transitive dependencies. It
 * does this in an incremental fashion by producing a txt file containing a sorted list of all
 * namespaces provided by the srcs listed in a {@code closure_js_library}. These files are then
 * accessed via the {@code --deps} flag on subsequent invocations for parent rules.
 */
public final class JsChecker {

  static {
    DiagnosticGroups.registerGroup("strictDependencies",
        JsCheckerFirstPass.DUPLICATE_PROVIDES,
        JsCheckerFirstPass.INVALID_SETTESTONLY,
        JsCheckerFirstPass.REDECLARED_PROVIDES,
        JsCheckerSecondPass.NOT_PROVIDED);
  }

  private static final ImmutableSet<String> WARNINGS =
      ImmutableSet.of(
          "checkTypes",
          "deprecated",
          "extraRequire",
          "lintChecks");

  private static final ImmutableSet<String> ERRORS =
      ImmutableSet.of(
          "checkRegExp",
          "extraRequire",
          "missingRequire",
          "strictDependencies");

  private static final ImmutableSet<DiagnosticType> DISABLE_FOR_ES6 =
      ImmutableSet.of(
          CheckJSDocStyle.MISSING_PARAMETER_JSDOC,
          CheckJSDocStyle.MISSING_RETURN_JSDOC,
          CheckJSDocStyle.OPTIONAL_PARAM_NOT_MARKED_OPTIONAL,
          CheckJSDocStyle.OPTIONAL_TYPE_NOT_USING_OPTIONAL_NAME);

  private enum Convention {
    CLOSURE(new JsCheckerClosureCodingConvention()),
    GOOGLE(new GoogleCodingConvention()),
    JQUERY(new JqueryCodingConvention());

    final CodingConventions.Proxy convention;

    private Convention(CodingConventions.Proxy convention) {
      this.convention = convention;
    }
  }

  private static final String USAGE =
      String.format("Usage:\n  java %s [FLAGS]\n", JsChecker.class.getName());

  @Option(
      name = "--label",
      usage = "Name of rule being compiled.")
  private String label = "//ohmygoth";

  @Option(
      name = "--src",
      usage = "JavaScript source files, sans externs.")
  private List<String> sources = new ArrayList<>();

  @Option(
      name = "--extern",
      usage = "JavaScript @externs source files.")
  private List<String> externs = new ArrayList<>();

  @Option(
      name = "--dep",
      usage = "foo-provides.txt files from deps targets.")
  private List<String> deps = new ArrayList<>();

  @Option(
      name = "--convention",
      usage = "Coding convention for linting.")
  private Convention convention = Convention.CLOSURE;

  @Option(
      name = "--language",
      usage = "Language spec of input sources.")
  private LanguageMode language = LanguageMode.ECMASCRIPT5_STRICT;

  @Option(
      name = "--suppress",
      usage = "Diagnostic types to not show as errors or warnings.")
  private List<String> suppress = new ArrayList<>();

  @Option(
      name = "--testonly",
      usage = "Indicates a testonly rule is being compiled.")
  private boolean testonly;

  @Option(
      name = "--output",
      usage = "Incremental -provides.txt report output filename.")
  private String output = "";

  @Option(
      name = "--output_errors",
      usage = "Name of output file for tee'd compiler errors.")
  private String outputErrors = "";

  @Option(
      name = "--nofail",
      usage = "Always return exit code 0.")
  private boolean nofail;

  @Option(
      name = "--help",
      usage = "Displays this message on stdout and exit")
  private boolean help;

  private boolean run() throws IOException {
    final JsCheckerState state = new JsCheckerState(label, testonly);
    final Set<String> actuallySuppressed = new HashSet<>();

    // map diagnostic codes back to groups
    Map<String, DiagnosticType> diagnosticTypes = new HashMap<>(256);
    DiagnosticGroups groups = new DiagnosticGroups();
    for (String groupName : Iterables.concat(WARNINGS, ERRORS)) {
      DiagnosticGroup group = groups.forName(groupName);
      for (DiagnosticType type : group.getTypes()) {
        state.diagnosticGroups.put(type, groupName);
        diagnosticTypes.put(type.key, type);
      }
    }

    // read provided files created by this program on deps
    for (String dep : deps) {
      state.provided.addAll(Files.readAllLines(Paths.get(dep), UTF_8));
    }

    // configure compiler
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    options.setLanguage(language);
    options.setCodingConvention(convention.convention);
    options.setSkipTranspilationAndCrash(true);
    options.setIdeMode(true);
    JsCheckerErrorManager manager =
        new JsCheckerErrorManager(state, new JsCheckerErrorFormatter(state, compiler));
    compiler.setErrorManager(manager);

    // configure which error messages appear
    for (String error : ERRORS) {
      options.setWarningLevel(groups.forName(error), CheckLevel.ERROR);
    }
    for (String warning : WARNINGS) {
      options.setWarningLevel(groups.forName(warning), CheckLevel.WARNING);
    }
    List<DiagnosticType> types = new ArrayList<>();
    for (String name : suppress) {
      DiagnosticGroup group = groups.forName(name);
      if (group != null) {
        options.setWarningLevel(group, CheckLevel.OFF);
        continue;
      }
      DiagnosticType type = diagnosticTypes.get(name);
      if (type == null) {
        System.err.println("Bad --suppress value: " + name);
        return false;
      }
      types.add(type);
    }
    if (language == LanguageMode.ECMASCRIPT6_STRICT || language == LanguageMode.ECMASCRIPT6_TYPED) {
      types.addAll(DISABLE_FOR_ES6);
    }
    if (!types.isEmpty()) {
      options.setWarningLevel(
          DiagnosticGroups.registerGroup("doodle",
              Iterables.toArray(types, DiagnosticType.class)),
          CheckLevel.OFF);
    }

    // don't show lint errors on generated files
    options.addWarningsGuard(
        new WarningsGuard() {
          @Override
          public CheckLevel level(JSError error) {
            if ("lintChecks".equals(state.diagnosticGroups.get(error.getType()))
                && (error.sourceName.contains("bazel-out/")
                    || error.sourceName.contains("bazel-genfiles/"))) {
              return CheckLevel.OFF;
            }
            return null;
          }
        });

    // keep track of DiagnosticType and DiagnosticGroup names where warnings were actually emitted,
    // so we can throw an error if the user needs to remove unneeded items from suppress attributes.
    options.addWarningsGuard(
        new WarningsGuard() {
          @Override
          public CheckLevel level(JSError error) {
            String typeName = error.getType().key;
            String groupName = state.diagnosticGroups.get(error.getType());
            if (groupName != null) {
              actuallySuppressed.add(typeName);
              actuallySuppressed.add(groupName);
            }
            return null;
          }
        });

    // run the compiler
    compiler.setPassConfig(new JsCheckerPassConfig(state, options));
    compiler.disableThreads();
    Result result = compiler.compile(getSourceFiles(externs), getSourceFiles(sources), options);
    boolean success = result.success;

    // check suppress attribute is tidy
    Set<String> useless = Sets.difference(ImmutableSet.copyOf(suppress), actuallySuppressed);
    if (!useless.isEmpty()) {
      state.stderr.add(
          String.format(
              "ERROR: The following suppress codes are superfluous and must be removed from the"
                  + " closure_js_library() build rule: %s\n\n - %s\n",
              label, Joiner.on("\n - ").join(useless)));
      success = false;
    }

    // write errors
    for (String line : state.stderr) {
      System.err.println(line);
    }
    if (!outputErrors.isEmpty()) {
      Files.write(Paths.get(outputErrors), state.stderr, UTF_8);
    }

    // write provided file
    if (!output.isEmpty()) {
      Files.write(Paths.get(output), state.provides, UTF_8);
    }

    if (!success) {
      return false;
    }

    return true;
  }

  private static ImmutableList<SourceFile> getSourceFiles(Iterable<String> filenames) {
    ImmutableList.Builder<SourceFile> result = new ImmutableList.Builder<>();
    for (String filename : filenames) {
      result.add(SourceFile.fromFile(filename));
    }
    return result.build();
  }

  public static void main(String[] args) throws IOException {
    JsChecker checker = new JsChecker();
    CmdLineParser parser = new CmdLineParser(checker);
    parser.setUsageWidth(80);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      System.err.println(USAGE);
      parser.printUsage(System.err);
      System.err.println();
      System.exit(1);
    }
    if (checker.help) {
      System.err.println(USAGE);
      parser.printUsage(System.out);
      System.out.println();
    } else {
      if (!checker.run()) {
        System.exit(checker.nofail ? 0 : 1);
      }
    }
  }
}
