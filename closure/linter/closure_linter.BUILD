# Copyright 2016 The Closure Rules Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

package(default_visibility = ["//visibility:public"])

py_library(
    name = "library",
    srcs = [
        # This list was generated by :library_files_maker
        "closure_linter/__init__.py",
        "closure_linter/aliaspass.py",
        "closure_linter/checker.py",
        "closure_linter/checkerbase.py",
        "closure_linter/closurizednamespacesinfo.py",
        "closure_linter/common/__init__.py",
        "closure_linter/common/error.py",
        "closure_linter/common/erroraccumulator.py",
        "closure_linter/common/errorhandler.py",
        "closure_linter/common/erroroutput.py",
        "closure_linter/common/filetestcase.py",
        "closure_linter/common/htmlutil.py",
        "closure_linter/common/lintrunner.py",
        "closure_linter/common/matcher.py",
        "closure_linter/common/position.py",
        "closure_linter/common/simplefileflags.py",
        "closure_linter/common/tokenizer.py",
        "closure_linter/common/tokens.py",
        "closure_linter/ecmalintrules.py",
        "closure_linter/ecmametadatapass.py",
        "closure_linter/error_check.py",
        "closure_linter/error_fixer.py",
        "closure_linter/errorrecord.py",
        "closure_linter/errorrules.py",
        "closure_linter/errors.py",
        "closure_linter/indentation.py",
        "closure_linter/javascriptlintrules.py",
        "closure_linter/javascriptstatetracker.py",
        "closure_linter/javascripttokenizer.py",
        "closure_linter/javascripttokens.py",
        "closure_linter/requireprovidesorter.py",
        "closure_linter/runner.py",
        "closure_linter/scopeutil.py",
        "closure_linter/statetracker.py",
        "closure_linter/testutil.py",
        "closure_linter/tokenutil.py",
        "closure_linter/typeannotation.py",
    ],
    visibility = ["//visibility:private"],
)

py_binary(
    name = "gjslint",
    srcs = ["closure_linter/gjslint.py"],
    main = "closure_linter/gjslint.py",
    deps = [
        "@python_gflags//:library",
        ":library",
    ],
)

py_binary(
    name = "fixjsstyle",
    srcs = ["closure_linter/fixjsstyle.py"],
    main = "closure_linter/fixjsstyle.py",
    deps = [
        "@python_gflags//:library",
        ":library",
    ],
)

# This rule needs to be re-run each time the Closure Linter is updated. They
# allow us to list all the files explicitly in the BUILD file. This makes Bazel
# go much faster for people who don't have SSDs, because it won't need to glob
# a gigantic directory structure each time the package is loaded.
#
# The reason why this rule is commented out is because all glob() functions are
# evaluated when the BUILD file is loaded, i.e. they're not lazy. Normally we'd
# solve this by putting these rules in a subpackage named glob. But we're only
# allowed to specify a single BUILD file for an external dependency. This is
# unfortunate, because that means there's no way to automatically test that the
# above list is correct.

# genrule(
#     name = "library_files_maker",
#     srcs = glob(
#         ["closure_linter/**/*.py"],
#         exclude = [
#             "closure_linter/**/*_test.py",
#             "closure_linter/fixjsstyle.py",
#             "closure_linter/gjslint.py",
#         ],
#     ),
#     outs = ["library_files_maker.txt"],
#     cmd = ("#/bin/bash\n" +
#            "{\n" +
#            "  for s in $(SRCS); do\n" +
#            "    echo \\\"$${s#external/closure_linter/}\\\",\n" +
#            "  done\n" +
#            "} | sort >$@"),
#     visibility = ["//visibility:private"],
# )
