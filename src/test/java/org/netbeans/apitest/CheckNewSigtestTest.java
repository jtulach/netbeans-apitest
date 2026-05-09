/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
package org.netbeans.apitest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import junit.framework.Test;
import org.netbeans.apitest.ExecuteUtils.ExecutionError;
import org.netbeans.junit.NbTestCase;
import org.netbeans.junit.NbTestSuite;

/**
 *
 * @author Jaroslav Tulach
 */
public class CheckNewSigtestTest extends NbTestCase {
    private static File workDir;
    
    public CheckNewSigtestTest(String s) {
        super(s);
    }
    
    public static Test suite() {
        Test t = null;
//        t = new CheckNewSigtestTest("testTwoDimArray");
        return t != null ? t : new NbTestSuite(CheckNewSigtestTest.class);
    }

    @Override
    protected void setUp() throws Exception {
        clearWorkDir();
    }

    public void testMissingFieldInAbstractClassIsDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public Object F;" +
            "}";
        createFile(1, "I.java", c1);
        
        
        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(2, "I.java", c2);
        
        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Missing field has to be detected");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }
    
    public void testConvertingFinalClassToAbstract() throws Exception {
        String c1 =
            "package ahoj;" +
            "  public final class F {" +
            "    public void collapseNode(java.lang.Object o) { }" +
            "    public void expandNode(java.lang.Object o) { }" +
            "    public boolean isExpanded(java.lang.Object o) { return false; }" +
            "  }" +
            "";
        createFile(1, "F.java", c1);
        
        
        String c2 =
            "package ahoj;" +
            "  public abstract class F {" +
            "    public abstract void collapseNode(java.lang.Object o);" +
            "    public abstract void expandNode(java.lang.Object o);" +
            "    public abstract boolean isExpanded(java.lang.Object o);" +
            "  }";
        createFile(2, "F.java", c2);
        
        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }
    
    
    public void testConvertingNonFinalClassToAbstract() throws Exception {
        String c1 =
            "package ahoj;" +
            "  public class F { " +
            "    public void collapseNode(java.lang.Object o) { }" +
            "    public void expandNode(java.lang.Object o) { }" +
            "    public boolean isExpanded(java.lang.Object o) { return false; }" +
            "  }";
        createFile(1, "F.java", c1);
        
        
        String c2 =
            "package ahoj;" +
            "  public abstract class F {" +
            "    public abstract void collapseNode(java.lang.Object o);" +
            "    public abstract void expandNode(java.lang.Object o);" +
            "    public abstract boolean isExpanded(java.lang.Object o);" +
            "  }";
        createFile(2, "F.java", c2);
        
        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Adding abstract methods is incompatible change");
        } catch (ExecutionError err) {
            // ok
        }
    }
    
    public void testMissingStaticFieldDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public static final int F = 1;" +
            "}";
        createFile(1, "I.java", c1);
        
        
        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(2, "I.java", c2);
        
        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Missing field has to be detected");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testAddingStaticFieldOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(1, "I.java", c1);
        
        
        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public static final int F = 1;" +
            "}";
        createFile(2, "I.java", c2);
        
        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }
    public void testKeepingStaticFieldOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public static final int F = 1;" +
            "}";
        createFile(1, "I.java", c1);
        
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testClassesWithAnnotations() throws Exception {
        String c1 =
            "package ahoj;" +
            "@Deprecated " +
            "public abstract class ServiceType extends Object implements java.io.Serializable{" +
            "}";
        createFile(1, "ServiceType.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }

    public void testArrayBackward() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class ServiceType {" +
            "  public abstract void get(String[] args);" +
            "}";
        createFile(1, "ServiceType.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testTwoDimArray() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface PrintProvider {" +
            "  String[][] getPages(int width, int height, double zoom);" +
            "}";
        createFile(1, "PrintProvider.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testContextAwareAction() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface ContextAwareAction extends javax.swing.Action {" +
            "    public javax.swing.Action createContextAwareInstance(Object ac);" +
            "}";
        createFile(1, "ContextAwareAction.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testOverrideFinalizeAsFinal() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class X extends Object {" +
            "    protected final void finalize() { }" +
            "}";
        createFile(1, "X.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testOverrideFinalize() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class X extends Object {" +
            "    protected void finalize() { }" +
            "}";
        createFile(1, "X.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testUseJavaXNet() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class X extends javax.net.SocketFactory {" +
            "}";
        createFile(1, "X.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testSystemActionEquals() throws Exception {
        String c0 =
            "package ahoj;" +
            "public class Y extends X {" +
            "}";
        String c1 =
            "package ahoj;" +
            "public class X extends Base {" +
            "    public boolean ahoj() { return false; }" +
            "}";
        String c2 =
            "package ahoj;" +
            "public class Base extends Object {" +
            "    protected boolean ahoj() { return true; }" +
            "}";
        createFile(1, "Y.java", c0);
        createFile(1, "X.java", c1);
        createFile(1, "Base.java", c2);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testJavaAPICanBeRead() throws Exception {
        File d1 = new File(getWorkDir(), "dir1");
        
        File build = new File(getWorkDir(), "build.xml");
        extractResource("build.xml", build);

        File sigFile = new File(getWorkDir(), "file.sig");
        extractResource("org-netbeans-api-java.sig", sigFile);
        
        List<String> args = new ArrayList<String>();
        //args.addAll(Arrays.asList(additionalArgs));
        args.add("compare");
        args.add("-Ddir1=" + d1);
        args.add("-Dsig=" + sigFile);
        ExecuteUtils.execute(build, args.toArray(new String[0]));
    }
    public void testEnums() throws Exception {
        String c1 =
            "package ahoj;" +
            "public enum Type {" +
            "  AHOJ" +
            "}";
        createFile(1, "Type.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testGenerics() throws Exception {
        String c1 =
            "package ahoj;" +
            "public final class ServiceType {" +
            "  public static <T extends java.util.EventListener> " +
            "    T create(Class<T> clazz, T inst) {" +
            "    return inst;" +
            "  }" +
            "}";
        createFile(1, "ServiceType.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testUnion2() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class Union2<First,Second> implements Cloneable {\n" +
            "  Union2() {}\n" +
            "  public abstract First first() throws IllegalArgumentException;\n" +
            "  public abstract Second second() throws IllegalArgumentException;\n" +
            "  @Override public abstract Union2<First,Second> clone();\n" +
            "  public static <First,Second> Union2<First,Second> createFirst(First first) { return null; }\n" +
            "  public static <First,Second> Union2<First,Second> createSecond(Second second) { return null; }\n" +
            "}";
        createFile(1, "Union2.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    /* XXX: The following shows a bug in the original sigtest signature file
     * generator. It demonstrates itself in api.visual module. Disabling the
     * test for now and fixing the broken golden file by hand.
    public void testInnerTwoConstrs() throws Exception {
        String c1 =
            "package ahoj;" +
            "import java.util.EnumSet;" +
            "public class Anchor {\n" +
            "  Anchor() {}\n" +
            "  public enum Kind {" +
            "  }" +
            "  public class Region {" +
            "    public Region(Integer x, Kind l) {}" +
            "    public Region(Integer x, EnumSet<Kind> l) {}" +
            "  }" +
            "}";
        createFile(1, "Anchor.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    */
    public void testTopoSort() throws Exception {
        String c1 =
            "package ahoj;" +
            "import java.util.*;" +
            "import java.io.IOException;" +
            "public abstract class Utilities {\n" +
            "  Utilities() {}\n" +
            "      public static <T> List<T> topologicalSort(" +
            "           Collection<T> c, " +
            "           Map<? super T, ? extends Collection<? extends T>> edges" +
            "      ) " +
            "      throws IOException {" +
            "        return null;" +
            "      }" +
            "}";
        createFile(1, "Utilities.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testSomeProblemInDebugger() throws Exception {
        String c1 =
            "package org.netbeans.api.debugger;" +
            "public final class DebuggerManager {\n" +
            "  DebuggerManager() {}\n" +
            "  public Breakpoint[] getBreakpoints () { return null; }\n" +
            "}";
        String c2 =
            "package org.netbeans.api.debugger;" +
            "public abstract class Breakpoint {\n" +
            "}";
        createFile(1, "DebuggerManager.java", c1);
        createFile(1, "Breakpoint.java", c2);
        checkAPIsEqual("-Dcheck.package=org.netbeans.api.debugger.*");
    }
    
    
    public void testPrimitiveArraysBackward() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class ServiceType {" +
            "  public abstract void get(long[] args);" +
            "  public abstract void get(int[] args);" +
            "  public abstract void get(short[] args);" +
            "  public abstract void get(byte[] args);" +
            "  public abstract void get(double[] args);" +
            "  public abstract void get(float[] args);" +
            "  public abstract void get(char[] args);" +
            "  public abstract int[] getICounts();" +
            "  public abstract short[] getSCounts();" +
            "  public abstract byte[] getBCounts();" +
            "  public abstract double[] getDCounts();" +
            "  public abstract char[] getCCounts();" +
            "  public abstract long[] getLCounts();" +
            "}";
        createFile(1, "ServiceType.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testVarArgsBackward() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class ServiceType {" +
            "  public abstract void get(String... args);" +
            "}";
        createFile(1, "ServiceType.java", c1);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }
    public void testFieldHidesField() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class ServiceBase extends Object {" +
            "    public javax.accessibility.AccessibleContext accessibleContext;" +
            "}";
        String c2 =
            "package ahoj;" +
            "public class ServiceType extends ServiceBase {" +
            "    private javax.accessibility.AccessibleContext accessibleContext;" +
            "}";
        createFile(1, "ServiceBase.java", c1);
        createFile(1, "ServiceType.java", c2);
        checkAPIsEqual("-Dcheck.package=ahoj.*");
    }

    public void testMissingConstructorInAbstractClassIsDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public I(String s) { " +
            "  }" +
            "}";
        createFile(1, "I.java", c1);
        
        
        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(2, "I.java", c2);
        
        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Missing field has to be detected");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testDefaultMethod() throws Exception {
        String i1 =
            "package libka;" +
            "public interface Iface {" +
            "}";
        String c1 =
            "package ahoj;" +
            "public abstract class Clz implements libka.Iface {" +
            "}";
        String i2 =
            "package libka;" +
            "public interface Iface {" +
                "public default void doNothing() {}" +
            "}";
        createFile(1, "Iface.java", i1);
        createFile(1, "Clz.java", c1);
        createFile(2, "Iface.java", i2);
        createFile(2, "Clz.java", c1);
        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddInterfaceMethod() throws Exception {
        String i1 =
            "package libka;" +
            "public interface Iface {" +
            "}";
        String c1 =
            "package ahoj;" +
            "public abstract class Clz implements libka.Iface {" +
            "}";
        String i2 =
            "package libka;" +
            "public interface Iface {" +
                "public void doNothing();" +
            "}";
        createFile(1, "Iface.java", i1);
        createFile(1, "Clz.java", c1);
        createFile(2, "Iface.java", i2);
        createFile(2, "Clz.java", c1);
        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Added method into interface");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testTypeAnnotation() throws Exception {
        String i1 =
            "package libka;" +
            "@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_PARAMETER)" +
            "public @interface Anno {" +
            "}";
        String c1 =
            "package ahoj;" +
            "public abstract class Clz<@libka.Anno T> extends java.lang.ref.WeakReference<T> {" +
            "  public Clz() { super(null); }" +
            "  public abstract T get();" +
            "}";
        String c2 =
            "package ahoj;" +
            "public abstract class Clz<@libka.Anno T> extends java.lang.ref.WeakReference<T> {" +
            "  public Clz() { super(null); }" +
            "  public T get() { return null; }" +
            "}";
        createFile(1, "Anno.java", i1);
        createFile(1, "Clz.java", c1);
        createFile(2, "Anno.java", i1);
        createFile(2, "Clz.java", c2);
        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");

        int err = ExecuteUtils.getStdErr().indexOf("ClassFormatError");
        assertEquals("No index out of bounds reading @Anno T:\n" + ExecuteUtils.getStdErr(), -1, err);
        int out = ExecuteUtils.getStdOut().indexOf("ClassFormatError");
        assertEquals("No index out of bounds reading @Anno T:\n" + ExecuteUtils.getStdOut(), -1, out);
    }

    protected final void createFile(int slot, String name, String content) throws Exception {
        File d1 = new File(getWorkDir(), "dir" + slot);
        File c1 = new File(d1, name);
        copy(content, c1);
    }
    
    protected void compareAPIs(int slotFirst, int slotSecond, String... additionalArgs) throws Exception {
        File d1 = new File(getWorkDir(), "dir" + slotFirst);
        File d2 = new File(getWorkDir(), "dir" + slotSecond);
        
        File build = new File(getWorkDir(), "build.xml");
        extractResource("build.xml", build);
        
        List<String> args = new ArrayList<String>();
        args.addAll(Arrays.asList(additionalArgs));
        args.add("-Ddir1=" + d1);
        args.add("-Ddir2=" + d2);
        ExecuteUtils.execute(build, args.toArray(new String[0]));
    }

    protected void checkAPIsEqual(String... additionalArgs) throws Exception {
        File d1 = new File(getWorkDir(), "dir1");
        
        File build = new File(getWorkDir(), "build.xml");
        extractResource("build.xml", build);
        
        List<String> args = new ArrayList<String>();
        args.addAll(Arrays.asList(additionalArgs));
        args.add("-Ddir1=" + d1);
        args.add("-Ddir2=" + d1);
        args.add("-Dcheck.type=check");
        ExecuteUtils.execute(build, args.toArray(new String[0]));
    }
    
    private static final void copy(String txt, File f) throws Exception {
        f.getParentFile().mkdirs();
        FileWriter w = new FileWriter(f);
        w.append(txt);
        w.close();
    }

    final File extractResource(String res, File f) throws Exception {
        URL u = CheckNewSigtestTest.class.getResource(res);
        assertNotNull ("Resource should be found " + res, u);
        
        FileOutputStream os = new FileOutputStream(f);
        InputStream is = u.openStream();
        for (;;) {
            int ch = is.read ();
            if (ch == -1) {
                break;
            }
            os.write (ch);
        }
        os.close ();
            
        return f;
    }
    
    final static String readFile (java.io.File f) throws java.io.IOException {
        int s = (int)f.length ();
        byte[] data = new byte[s];
        assertEquals ("Read all data", s, new java.io.FileInputStream (f).read (data));
        
        return new String (data);
    }
    
    final File extractString (String res, String nameExt) throws Exception {
        File f = new File(getWorkDir(), nameExt);
        f.deleteOnExit ();
        
        FileOutputStream os = new FileOutputStream(f);
        InputStream is = new ByteArrayInputStream(res.getBytes("UTF-8"));
        for (;;) {
            int ch = is.read ();
            if (ch == -1) {
                break;
            }
            os.write (ch);
        }
        os.close ();
            
        return f;
    }
    
}