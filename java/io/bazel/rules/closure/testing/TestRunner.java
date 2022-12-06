/*
 * Copyright 2022 The Closure Rules Authors. All rights reserved.
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

package io.bazel.rules.closure.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import io.bazel.rules.closure.webfiles.server.DaggerWebfilesServer_Server;
import io.bazel.rules.closure.webfiles.server.WebfilesServer;
import java.nio.file.FileSystems;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ServerSocketFactory;

/**
 * The test runner to run test against browsers.
 *
 * <p>This program starts an HTTP server that serves runfiles. It uses a webdriver to load the
 * generated test runner HTML file on the browser. Once the page is loaded, it polls the Closure
 * Library repeatedly to check if the tests are finished, and logs results.
 */
class TestRunner {

  public static void main(String args[]) throws Exception {

    String serverConfig = args[0];
    String htmlWebpath = args[1];
    if (!htmlWebpath.startsWith("/")) {
      htmlWebpath = "/" + htmlWebpath;
    }

    WebfilesServer server = WebfilesServer.create(ImmutableList.of(serverConfig));
    HostAndPort hostAndPort = server.spawn();
    String address = hostAndPort.toString();

    String htmlURL = "http://" + address + htmlWebpath;
    TestDriver driver = new TestDriver(htmlURL);
    driver.run();

    // TODO(phpham): Find out how to shutdown the server
    server.shutdown();
    System.exit(0);
  }
}

