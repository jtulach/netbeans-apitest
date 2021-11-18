/*
 * $Id: ClasspathImpl.java 4516 2008-03-17 18:48:27Z eg216457 $
 *
 * Copyright 1996-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tdk.signaturetest.classpath;

import com.sun.tdk.signaturetest.SigTest;
import com.sun.tdk.signaturetest.model.ExoticCharTools;
import com.sun.tdk.signaturetest.util.I18NResourceBundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;

/**
 * <b>ClasspathImpl</b> provides access to all classes placed inside
 * directories and/or jar-files listed in the classpath, which is given
 * to the constructor for new <b>ClasspathImpl</b> instance.
 * </p>
 * <p>The constructor searches every directory or jar-file listed and keeps
 * corresponding <b>ClasspathEntry</b> element, which can provide access to a bytecode
 * for each class found inside the directory or jar-file. All classes found inside
 * the listed directories and jar-files are virtually enumerated in the same
 * order as they are found. The methods <code>nextClassName()</code> and
 * <code>setListToBegin()</code> provide access to this classes enumeration.
 * <p/>
 * <p>Also, the method <code>findClass(name)</code> provides access to class
 * directly by its qualified name. Note however, that the names class must belong
 * to some directory or zip-file pointed to the <b>ClasspathImpl</b> instance.
 *
 * @author Maxim Sokolnikov
 * @author Roman Makarchuk
 * @version 05/03/22
 * @see com.sun.tdk.signaturetest.classpath.ClasspathEntry
 */
public class ClasspathImpl implements Classpath {
    private final Release release;

    /*
    public class ClassIterator {

        public boolean hasNext() {
            return false;
        }

        public String next() {
            return null;
        }
    }
*/

    /**
     * Reference to the class implementing <b>DirectoryEntry</b>.
     *
     * @see DirectoryEntry
     * @see ClasspathEntry
     */
    private static final String DIRECTORY_ENTRY_IMPL = "com.sun.tdk.signaturetest.classpath.DirectoryEntry";
    /**
     * Reference to the class implementing <b>JarFileEntry</b>.
     *
     * @see JarFileEntry
     * @see ClasspathEntry
     */
    private static final String JAR_ENTRY_IMPL = "com.sun.tdk.signaturetest.classpath.JarFileEntry";

    /**
     * Collector for errors and warnings occurring while <b>ClasspathImpl</b>
     * constructor searches archives of classes.
     */
    private List errors;

    /**
     * Number of ignorable entries found in the path given to <b>ClasspathImpl</b>
     * constructor.
     */
    private int sizeIgnorables;

    /**
     * List of <b>ClasspathEntry</b> instances referring to directories and zip-files
     * found by <b>ClasspathImpl</b> constructor.
     *
     * @see com.sun.tdk.signaturetest.classpath.ClasspathEntry
     * @see com.sun.tdk.signaturetest.classpath.DirectoryEntry
     * @see JarFileEntry
     */
    private List entries;


    private Iterator iterator;

    /**
     * <I>Current</I> directory or zip-file entry, containing <I>current</I>
     * class. This field is used to organize transparent enumeration of all
     * classes found by this <b>ClasspathImpl</b> instance.
     *
     * @see #nextClassName()
     * @see #setListToBegin()
     */
    private ClasspathEntry currentEntry;

    /**
     * Path separator used by operating system.
     * Note, that <code>pathSeparator</code> is uniquely determined
     * when JVM starts.
     */
    private static String pathSeparator;

    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(ClasspathImpl.class);

    /**
     * Try to determine path separator used by operating system.
     * Path separator is found in <code>java.io.File</code>, or by
     * <code>System.getProperty()</code> invocation.
     *
     * @see #pathSeparator
     *
     * @see java.io.File#pathSeparator
     * @see System#getProperty(String)
     */
    static {
        try {
            // java.io.File is optional class and could be not implemented.
            Class c = Class.forName("java.io.File");
            Field f = c.getField("pathSeparator");
            pathSeparator = (String) f.get(null);
        } catch (Throwable t) {
            try {
                pathSeparator = System.getProperty("path.separator");
            } catch (SecurityException e) {
                if (SigTest.debug)
                    e.printStackTrace();
            }
        }
    }

    /**
     * This constructor finds all classes within the given classpath,
     * and creates a list of <b>ClasspathEntry</b> iterator - one element per
     * each directory or zip-file found. Classes found inside the listed
     * directories and zip files become available through the created
     * <b>ClasspathImpl</b> instance.
     *
     * @param release specify how to search JDK's API when class isn't found on class path
     * @param classPath Path string listing directories and/or zip files.
     * @throws SecurityException The <code>classPath</code> string has
     *                           invalid format.
     * @see #findClass(String)
     * @see #nextClassName()
     * @see #setListToBegin()
     * @see #createPathEntry(ClasspathEntry, String)
     */
    public ClasspathImpl(Release release, String classPath) {
        this.release = release;
        init(classPath);
    }


    public void init(String classPath) {
        entries = new ArrayList();
        errors = new ArrayList();
        Set unique = new HashSet();
        String path = (classPath == null) ? "" : classPath;
        if (!path.equals("") && (pathSeparator == null))
            throw new SecurityException(i18n.getString("ClasspathImpl.error.notdefinepathsep"));


        ClasspathEntry previosEntry = null;

        //creates Hashtable with ZipFiles and directories from path.
        while (path != null && path.length() > 0) {
            String s;
            int index = path.indexOf(pathSeparator);
            if (index < 0) {
                s = path;
                path = null;
            } else {
                s = path.substring(0, index);
                path = path.substring(index + pathSeparator.length());
            }

            if (unique.contains(s)) {
                errors.add(i18n.getString("ClasspathImpl.error.duplicate_entry_found", s));
                continue;
            }

            unique.add(s);

            ClasspathEntry entry = createPathEntry(previosEntry, s);
            if (entry != null && !entry.isEmpty()) {
                entries.add(entry);
                previosEntry = entry;
            }
        }

        setListToBegin();
    }


    public void close() {
        if (entries != null) {
            for (Iterator e = entries.iterator(); e.hasNext();)
                ((ClasspathEntry) e.next()).close();

            entries = null;
            iterator = null;
            currentEntry = null;
        }
    }


    public boolean isEmpty() {
        return entries.isEmpty();
    }


    /**
     * Report about all errors occurred while construction of ClasspathImpl.
     *
     * @param out Where to println error messages.
     */
    public void printErrors(PrintWriter out) {
        if (out != null)
            for (int i = 0; i < errors.size(); i++)
                out.println((String) errors.get(i));
    }

    /**
     * Return number of significand errors occurred when <b>ClasspathImpl</b>
     * constructor was being working. Ignorable path entries are not
     * taken into account here.
     *
     * @see #createPathEntry(ClasspathEntry, String)
     */
    public int getNumErrors() {
        return errors.size() - sizeIgnorables;
    }

    /**
     * Reset list of directories and/or zip-files found by <b>ClasspathImpl</b>.
     * This also resets transparent enumeration of classes found inside those
     * directories and zip-files, which are available with the methods
     * <code>nextClassName()</code>, <code>getCurrentClass()</code>, or
     * <code>findClass(name)</code>.
     *
     * @see #nextClassName()
     * @see #findClass(String)
     */
    public void setListToBegin() {
        iterator = entries.iterator();
        currentEntry = null;
        if (iterator.hasNext())
            currentEntry = (ClasspathEntry) iterator.next();
    }

    public boolean hasNext() {
        if (currentEntry == null)
            return false;

        if (currentEntry.hasNext())
            return true;

        currentEntry = null;
        if (iterator.hasNext()) {
            currentEntry = (ClasspathEntry) iterator.next();
            return hasNext();
        }

        return false;
    }

    /**
     * Search next class in the enumeration of classes found inside
     * directories and jar-files pointed to <code>this</code>
     * <b>ClasspathImpl</b> instance. You may invoke
     * <code>setListToBegin()</code> method to restore classes
     * enumeration to its starting point.
     *
     * @return Class qualified name
     * @see #setListToBegin()
     * @see #findClass(String)
     */
    public String nextClassName() {
        return currentEntry.nextClassName();
    }

    /**
     * Returns <b>FileInputStream</b> instance providing bytecode for the
     * required class. The class must be found by the given qualified name
     * inside some of <b>ClasspathEntry</b> iterator listed by <code>this</code>
     * <b>ClasspathImpl</b> instance.
     *
     * @param name Qualified name of the class requested.
     * @throws ClassNotFoundException Not found in any <b>ClasspathEntry</b>
     *                                in <code>this</code> <b>ClasspathImpl</b> instance.
     * @see java.io.FileInputStream
     */
    public InputStream findClass(String name) throws IOException, ClassNotFoundException {
        name = ExoticCharTools.decodeExotic(name);

        // generic names are no allowed here
        assert(name.indexOf('<') == -1 && name.indexOf('>') == -1);

        for (Iterator e = entries.iterator(); e.hasNext();) {
            try {
                return ((ClasspathEntry) e.next()).findClass(name);
            } catch (ClassNotFoundException exc) {
                // just skip this entry
            }
        }
        if (release != null) {
            InputStream is = release.findClass(name);
            if (is != null) {
                return is;
            }
        }
        throw new ClassNotFoundException(name);
    }

    /**
     * Check if the given name is directory or zip-file name,
     * and create either new <b>DirectoryEntry</b> or new
     * <b>JarFileEntry</b> instance correspondingly.
     *
     * @param name Qualified name of some directory or zip file.
     * @return New <b>ClasspathEntry</b> instance corresponding to
     *         the given <code>name</code>.
     */
    protected ClasspathEntry createPathEntry(ClasspathEntry previosEntry, String name) {
        // try to create directory
        Throwable t = null;
        if (new File(name).getName().equals("modules") || name.endsWith(".jimage")) {
            try {
                return new JimageJakeEntry(previosEntry, name);
            } catch (Throwable th) {
                t = th;
            }
        } else if (new File(name).isDirectory()) {
            try {
                Class c = Class.forName(DIRECTORY_ENTRY_IMPL);
                Constructor ctor = c.getConstructor(new Class[]{ClasspathEntry.class, String.class});
                return (ClasspathEntry) ctor.newInstance(new Object[]{previosEntry, name});
            } catch (InvocationTargetException e) {
                t = e.getTargetException();
            } catch (Throwable th) {
                t = th;
            }
        } else {
            // try to create JarFile entry
            try {
                Class c = Class.forName(JAR_ENTRY_IMPL);
                Constructor ctor = c.getConstructor(new Class[]{ClasspathEntry.class, String.class});
                return (ClasspathEntry) ctor.newInstance(new Object[]{previosEntry, name});
            } catch (InvocationTargetException e) {
                t = e.getTargetException();
            } catch (Throwable th) {
                t = th;
            }
        }
        if (t != null) {
            String invargs[] = {name, t.getMessage()};
            errors.add(i18n.getString("ClasspathImpl.error.ignoring", invargs));
            sizeIgnorables++;
        }

        return null;
    }
}

