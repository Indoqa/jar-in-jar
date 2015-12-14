/*
 * Licensed to the Indoqa Software Design und Beratung GmbH (Indoqa) under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Indoqa licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.indoqa.jarinjar;

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

/**
 * Reference this class if you want to run a Java application that is wrapped by Jar-in-Jar as a Java service using JSVC.
 *
 * @see "http://stackoverflow.com/questions/7687159/how-to-convert-a-java-program-to-daemon-with-jsvc/7687991#7687991"
 * @see "http://commons.apache.org/proper/commons-daemon/"
 * @see "http://commons.apache.org/proper/commons-daemon/jsvc.html"
 */
public class Daemon implements org.apache.commons.daemon.Daemon {

    private DaemonContext context;

    @Override
    public void destroy() {
        // nothing to do
    }

    @Override
    public void init(DaemonContext deamonContext) throws DaemonInitException, Exception {
        this.context = deamonContext;
    }

    @Override
    public void start() throws Exception {
        Main.main(this.context.getArguments());
    }

    @Override
    public void stop() throws Exception {
        // nothing to do
    }
}
