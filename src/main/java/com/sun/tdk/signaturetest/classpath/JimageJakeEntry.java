/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tdk.signaturetest.classpath;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Based on work originally done by on https://github.com/openjdk/sigtest:
 * @author Mike Ershov
 * @author Victor Rudometov
 *
 * Commit history range for file JimageJakeEntry at time of copying, is from https://github.com/openjdk/sigtest is a0db6ab4481a7d6ba0a50a77a6c1548d0fa528ee - 47e6c111549a8f04c273b859b042aba7cf792e64
 * Or https://github.com/openjdk/sigtest/compare/a0db6ab4481a7d6ba0a50a77a6c1548d0fa528ee..47e6c111549a8f04c273b859b042aba7cf792e64
 *
 */
public class JimageJakeEntry extends ClasspathEntry {

    private final List<DirectoryEntry> module_homes = new ArrayList<>();
    private int cur_module_index = -1;
    private static final String JIMAGE_FOLDER = System.getProperty("jimage.dir", System.getProperty("java.io.tmpdir") + File.separatorChar + ".jimage");
    private static final String MODULE_INFO_CLASS = "module-info.class";

    public JimageJakeEntry(ClasspathEntry previous, String name) throws IOException {
        super(previous);
        init(name);
    }

    @Override
    public void init(String jimageName) throws IOException {

        File baseDir = new File(JIMAGE_FOLDER);
        if (!baseDir.exists()) {
            Properties sysProps = System.getProperties();
            String javaHome = (String) sysProps.get("java.home");
            String util = javaHome + File.separatorChar + "bin" + File.separatorChar + "jimage";
            String inputModules = javaHome + File.separatorChar + "lib" + File.separatorChar + "modules";
            try {
                Process process = new ProcessBuilder(util, "extract", "--dir", baseDir.getPath(), inputModules).start();
                int ret = process.waitFor();
                if (ret != 0) {
                    throw new IOException("failure invoking jimage --dir " + baseDir.getPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        DirectoryEntry prevEntry = null;
        for (File f : baseDir.listFiles()) {
            if (f.isDirectory()) {

                File mi = new File(f, MODULE_INFO_CLASS);
                if (mi.exists()) {
                    mi.delete();
                }

                DirectoryEntry de = new DirectoryEntry(prevEntry, f.getAbsolutePath());
                module_homes.add(de);
                prevEntry = de;
            }
        }
        setFirstModule();

    }

    @Override
    public InputStream findClass(String name) throws IOException, ClassNotFoundException {
        for (DirectoryEntry module : module_homes) {
            try {
                return module.findClass(name);
            } catch (ClassNotFoundException | IOException e) {
                // just skip to the next
            }
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    public boolean hasNext() {
        if (isEmpty()) {
            return false;
        }
        do {
            if (getCurrentModule().hasNext()) {
                return true;
            }
        } while (nextModule() != null);
        return false;
    }

    @Override
    public String nextClassName() {
        if (!hasNext()) {
            return null;
        }
        return getCurrentModule().nextClassName();
    }

    @Override
    public void setListToBegin() {
        setFirstModule();
        getCurrentModule().setListToBegin();
    }

    @Override
    protected boolean contains(String className) {
        for (DirectoryEntry module : module_homes) {
            if (module.contains(className)) {
                return true;
            }
        }
        // not found? refer back
        return (previousEntry != null && previousEntry.contains(className));
    }

    @Override
    public boolean isEmpty() {
        for (DirectoryEntry module : module_homes) {
            if (!module.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private DirectoryEntry getCurrentModule() {
        return module_homes.get(cur_module_index);
    }

    private DirectoryEntry nextModule() {
        if (cur_module_index == module_homes.size() - 1) {
            return null;
        }
        DirectoryEntry res = module_homes.get(++cur_module_index);
        res.setListToBegin();
        return res;
    }

    private void setFirstModule() {
        if (module_homes.isEmpty()) {
            cur_module_index = -1;
        } else {
            cur_module_index = 0;
        }
    }


    @Override
    public void close() {

    }


}
