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
import static org.junit.Assert.assertNotEquals;
import org.netbeans.junit.NbTestCase;
import org.netbeans.junit.NbTestSuite;

/**
 *
 * @author Jaroslav Tulach
 */
public class APITest extends NbTestCase {
    private static File workDir;

    public APITest(String s) {
        super(s);
    }

    public static Test suite() {
        Test t = null;
//        t = new APITest("testAddMethodInAnInterfaceAllowedInSpecialMode");
        if (t == null) {
            t = new NbTestSuite(APITest.class);
        }
        return t;
    }

    @Override
    protected void setUp() throws Exception {
        clearWorkDir();
    }

    public void testWeakReference() throws Exception {
        String c1 = """
                package ahoj;

                import java.lang.ref.ReferenceQueue;
                import java.lang.ref.WeakReference;

                public final class MyWeakReference<T> extends WeakReference<T> {

                    public MyWeakReference(T t) {
                        super(t);
                    }

                    public MyWeakReference(T referent, ReferenceQueue<? super T> q) {
                        super(referent, q);
                    }
                }
                """;
        createFile(1, "MyWeakReference.java", c1);


        String c2 = """
                package ahoj;

                import java.lang.ref.ReferenceQueue;
                import java.lang.ref.WeakReference;

                public final class MyWeakReference<T> extends WeakReference<T> {

                    public MyWeakReference(T t) {
                        super(t);
                    }

                    public MyWeakReference(T referent, ReferenceQueue<? super T> q) {
                        super(referent, q);
                    }
                }
                """;
        createFile(2, "MyWeakReference.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");

        int err = ExecuteUtils.getStdErr().indexOf("ClassFormatError");
        assertEquals("No index out of bounds reading @Anno T:\n" + ExecuteUtils.getStdErr(), -1, err);
        int out = ExecuteUtils.getStdOut().indexOf("ClassFormatError");
        assertEquals("No index out of bounds reading @Anno T:\n" + ExecuteUtils.getStdOut(), -1, out);
    }

    public void testAddingObjectMethodToAnInterfaceIsOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface I {" +
            "  public static final class ObjSub {}" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public interface I {" +
            "  public static final class ObjSub {}" +
            "  String toString(); " +
            "}";
        createFile(2, "I.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddingMethodToEnumIsOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public enum E {" +
            "  A;" +
            "}";
        createFile(1, "E.java", c1);


        String c2 =
            "package ahoj;" +
            "public enum E {" +
            "  A;" +
            "  public void get() {};" +
            "}";
        createFile(2, "E.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddingInterfaceMethodToEnumIsOK() throws Exception {
        String c1 =
                "package ahoj;"
                + "public enum E {"
                + "  A;"
                + "}";
        createFile(1, "E.java", c1);


        String c2 =
                "package ahoj;"
                + "public enum E implements Runnable {"
                + "  A {public void run() {} };"
                + "}";
        createFile(2, "E.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddingInterfaceMethodToFinalClassIsOK() throws Exception {
        String c1 =
                "package ahoj;"
                + "public abstract class C {"
                + "  private C() {}"
                + "}";
        createFile(1, "C.java", c1);


        String c2 =
                "package ahoj;"
                + "public abstract class C implements Runnable {"
                + "  private C(){} "
                + "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testMissingMethodInAnInterfaceIsDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface I {" +
            "  public void get();" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public interface I {" +
            "}";
        createFile(2, "I.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Do not remove methods from interfaces");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testProblemsWithInnerInterface() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class Utils {" +
            "  public static interface I { }" +
            "}";

        createFile(1, "Utils.java", c1);
        createFile(2, "Utils.java", c1);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testProblemsWithInnerClass() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class Utils {" +
            "  public static class I { }" +
            "}";

        createFile(1, "Utils.java", c1);
        createFile(2, "Utils.java", c1);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddingStaticFieldToInterfaceIsOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface Constants {" +
            "}";
        String c2 =
            "package ahoj;" +
            "public interface Constants {" +
            "  public final static String HIDE_WHEN_DISABLED = \"hide\";" +
            "}";

        createFile(1, "Constants.java", c1);
        createFile(2, "Constants.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testProblemsWithInnerClassChanged() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class Utils {" +
            "  public STATIC class I { }" +
            "}";

        createFile(1, "Utils.java", c1.replace("STATIC", "static"));
        createFile(2, "Utils.java", c1.replace("STATIC", ""));

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("This is an incompatible change");
        } catch (ExecuteUtils.ExecutionError err) {
            // OK
        }
    }

    public void testAddMethodInAnInterfaceIsDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface I {" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public interface I {" +
            "  public void get();" +
            "}";
        createFile(2, "I.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Adding new methods to interfaces is not polite");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testAddStaticMethodIsOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public static I get() { return null; }" +
            "}";
        createFile(2, "I.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testMakeConstructorOfAbstractClassProtectedIsOK() throws Exception {
       String c1 =
            "package ahoj;" +
            " public abstract class A {" +
            "   public A() {}" +
            "}";
        createFile(1, "A.java", c1);


        String c2 =
            "package ahoj;" +
            " public abstract class A {" +
            "   protected A() {}" +
            "}";
        createFile(2, "A.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddMethodInAnInterfaceAllowedInSpecialMode() throws Exception {
        String c1 =
            "package ahoj;" +
            "public interface I {" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public interface I {" +
            "  public void get();" +
            "}";
        createFile(2, "I.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*", "-Dcheck.type=binarycheck");
    }

    public void testMakingAClassNonFinalIsNotIncompatibleChange() throws Exception {
        String c1 =
            "package ahoj;" +
            "public final class C {" +
            "}";
        createFile(1, "C.java", c1);


        String c2 = c1.replaceAll("final", "");
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testMakingNonSubclassableClassNonFinalIsNotIncompatibleChange() throws Exception {
        String c1 =
            "package ahoj;" +
            "public final class C {" +
            "  private C() { }" +
            "  public final int get() BODY" +
            "}";
        createFile(1, "C.java", c1.replace("BODY", "{ return 0; }"));


        String c2 = c1.replaceAll("final", "abstract");
        createFile(2, "C.java", c2.replace("BODY", ";"));

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testMakingNonSubclassableClassFinalIsNotIncompatibleChange() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class C {" +
            "  private C() {}" +
            "  public abstract int get() BODY" +
            "}";
        createFile(1, "C.java", c1.replace("BODY", ";"));

        String c2 = c1.replaceAll("abstract", "final");
        createFile(2, "C.java", c2.replace("BODY", "{ return 0; }"));

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testMakingNonSubclassableClassPackagePrivateIsIncompatibleChange() throws Exception {
        String template =
            "package ahoj;" +
            "VIS abstract class C {" +
            "  private C() {}" +
            "  public abstract int get() BODY" +
            "}";
        final String c1 = template.replace("BODY", ";").replace("VIS", "public");
        createFile(1, "C.java", c1);

        String c2tmp = template.replaceAll("abstract", "final");
        final String c2 = c2tmp.replace("BODY", "{ return 0; }").replace("VIS", "");
        createFile(2, "C.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Class cannot become invisible");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
            if (!ex.getMessage().contains("API type removed")) {
                throw ex;
            }
        }
    }

    public void testMakingNonSubclassableInnerClassNonFinalIsNotIncompatibleChange() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class C {" +
            "  public static ACCESS class Inner {" +
            "    private Inner() { }" +
            "    public METHOD int get() BODY" +
            "  }" +
            "}";
        createFile(1, "C.java", c1.replace("BODY", "{ return 0; }").replace("ACCESS", "").replace("METHOD", "final"));
        createFile(2, "C.java", c1.replace("BODY", ";").replace("ACCESS", "abstract").replace("METHOD", "abstract"));

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testSQLQuoter() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class C {" +
            " public static SQLQuoter createQuoter() { return null; } " +
            "  public static ACCESS class SQLQuoter {" +
            "    private SQLQuoter() { }" +
            "    public METHOD int get() BODY" +
            "  }" +
            "}";
        createFile(1, "C.java", c1.replace("BODY", "{ return 0; }").replace("ACCESS", "").replace("METHOD", ""));


        createFile(2, "C.java", c1.replace("BODY", ";").replace("ACCESS", "abstract").replace("METHOD", "abstract"));

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testSQLQuoterFinal() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class C {" +
            " public static SQLQuoter createQuoter() { return null; } " +
            "  public static ACCESS class SQLQuoter {" +
            "    private SQLQuoter() { }" +
            "    public METHOD int get() BODY" +
            "  }" +
            "}";
        createFile(1, "C.java", c1.replace("BODY", "{ return 0; }").replace("ACCESS", "final").replace("METHOD", ""));
        createFile(2, "C.java", c1.replace("BODY", ";").replace("ACCESS", "abstract").replace("METHOD", "abstract"));

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testSQLQuoterFinalAndNewAbstract() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class C {" +
            " public static SQLQuoter createQuoter() { return null; } " +
            "  public static ACCESS class SQLQuoter {" +
            "    private SQLQuoter() { }" +
            "    public METHOD int get() BODY" +
            "    ADD" +
            "  }" +
            "}";
        String method = "public abstract int additionalMethod();";
        createFile(1, "C.java", c1.replace("BODY", "{ return 0; }").replace("ACCESS", "final").replace("METHOD", "").replace("ADD", ""));
        createFile(2, "C.java", c1.replace("BODY", ";").replace("ACCESS", "abstract").replace("METHOD", "abstract").replace("ADD", method));

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testGenericsOverridenType() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class A<T> {" +
            "  public T get() { return null; }" +
            "  public class B extends A<String> {" +
            "    public String get() { return \"\"; }" +
            "  }\n" +
            "}";
        createFile(1, "A.java", c1);
        createFile(2, "A.java", c1);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testMissingMethodInAbstractClassIsDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public abstract void get();" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(2, "I.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Missing method has to be detected");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testRenamedInnerClassDetected() throws Exception {
        String c1 = ""
                + "package ahoj;"
                + "public interface Platform {\n"
                + "    interface Aarch64 extends Platform {\n"
                + "    }\n"
                + "}\n"
                + "";
        createFile(1, "Platform.java", c1);


        String c2 = ""
                + "package ahoj;"
                + "public interface Platform {\n"
                + "    interface AARCH64 extends Platform {\n"
                + "    }\n"
                + "}\n"
                + "";
        createFile(2, "Platform.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Rename of an inner interface is noticed as an error");
        } catch (ExecuteUtils.ExecutionError ex) {
            final int at = ExecuteUtils.getStdErr().indexOf("API type removed");
            assertNotEquals("API type removed error found", -1, at);
        }
    }

    public void testAbstractPackagePrivateMethodIsOK() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "  I() {}" +
            "  abstract void get();" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "  I() {}" +
            "}";
        createFile(2, "I.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddMethodInAbstractClassIsDetected() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public abstract void get();" +
            "}";
        createFile(2, "I.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Added method has to be detected");
        } catch (ExecuteUtils.ExecutionError ex) {
            // ok
        }
    }

    public void testChangeOfStaticFieldTypeInNetBeans12() throws Exception {
        String c1 =
            "package ahoj;" +
            "public class I {" +
            "  public I() {}" +
            "  protected static java.util.HashSet instances;" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public class I {" +
            "  public I() {}" +
            "  protected static java.util.Set<java.lang.String> instances;" +
            "}";
        createFile(2, "I.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
            fail("Change of field type to Set has to be detected");
        } catch (ExecuteUtils.ExecutionError ex) {
            assertNotEquals(ex.getMessage(), -1, ex.getMessage().indexOf("E4.1 - Changing field type"));
        }
    }

    public void testAddProtectedIsFine() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "  protected void get() { }" +
            "}";
        createFile(2, "I.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testAddPublicIsFine() throws Exception {
        String c1 =
            "package ahoj;" +
            "public abstract class I {" +
            "}";
        createFile(1, "I.java", c1);


        String c2 =
            "package ahoj;" +
            "public abstract class I {" +
            "  public void get() { }" +
            "}";
        createFile(2, "I.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testOverridenTypeChanged() throws Exception {
        String c1 =
            "package ahoj; import java.io.IOException; " +
    "public class W implements Appendable {" +
    "    public Appendable append(CharSequence csq) throws IOException { return this; }" +
    "    public Appendable append(CharSequence csq, int start, int end) throws IOException { return this; }" +
    "    public Appendable append(char c) throws IOException { return this; }" +
    "}";
        String c2 = c1.replaceAll("public Appendable", "public W");
        createFile(1, "W.java", c1);
        createFile(2, "W.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*");
    }

    public void testGenerateVersionNumber() throws Exception {
        String retAppendable =
            "package ahoj; import java.io.IOException; " +
    "public class W implements Appendable {" +
    "    public Appendable append(CharSequence csq) throws IOException { return this; }" +
    "    public Appendable append(CharSequence csq, int start, int end) throws IOException { return this; }" +
    "    public Appendable append(char c) throws IOException { return this; }" +
    "}";
        String retW = retAppendable.replaceAll("public Appendable", "public W");
        createFile(1, "W.java", retW);
        createFile(2, "W.java", retAppendable);

        try {
            compareAPIs(1, 2, "-Dcheck.package=ahoj.*", "with-version", "-Dv1=1.1", "-Dv2=3.3");
            fail("Should be an incompatible change");
        } catch (ExecuteUtils.ExecutionError incompat) {
            // ok
            if (!ExecuteUtils.getStdOut().contains("1.1")) {
                fail("Should report 1.1:\n" + ExecuteUtils.getStdErr());
            }
            if (!ExecuteUtils.getStdErr().contains("3.3")) {
                fail("Should report 3.3:\n" + ExecuteUtils.getStdErr());
            }
        }
    }

    public void testGenerateVersionNumberAsJunit() throws Exception {
        String retAppendable =
            "package ahoj; import java.io.IOException; " +
    "public class W implements Appendable {" +
    "    public Appendable append(CharSequence csq) throws IOException { return this; }" +
    "    public Appendable append(CharSequence csq, int start, int end) throws IOException { return this; }" +
    "    public Appendable append(char c) throws IOException { return this; }" +
    "}";
        String retW = retAppendable.replaceAll("public Appendable", "public W");
        createFile(1, "W.java", retW);
        createFile(2, "W.java", retAppendable);

        File report = new File(getWorkDir(), "report.xml");
        report.delete();

        compareAPIs(1, 2, "-Dcheck.package=ahoj.*", "with-version-junit", "-Dv1=1.1", "-Dv2=3.3", "-Dcheck.report=" + report, "-Dsigtest.mail=jarda@darda.petarda.org");

        assertTrue("Report exists", report.exists());
        String in = readFile(report);
        // ok
        if (!in.contains("1.1")) {
            fail("Should report 1.1:\n" + in);
        }
        if (!in.contains("3.3")) {
            fail("Should report 3.3:\n" + in);
        }
        if (!in.contains("email: jarda@darda.petarda.org")) {
            fail("Should contain email:\n" + in);
        }
    }

    public void testNoFailuresIfInXMLIf() throws Exception {
        String retAppendable =
            "package ahoj; import java.io.IOException; " +
            "public class W {" +
                "  public void get(X args) { }" +
                "} class X {} ";
        createFile(1, "W.java", retAppendable);
        createFile(2, "W.java", retAppendable);

        File report = new File(getWorkDir(), "report.xml");
        report.delete();

        compareAPIs(1, 2,
            "generate",
            "-Dcheck.package=ahoj.*",
            "-Dcheck.report=" + report,
            "-Dfail.on.error=false"
        );

        assertTrue("Report exists", report.exists());
        String in = readFile(report);
        if (!in.contains("failures=\"1\"")) {
            fail("Should contain failures='1':\n" + in);
        }
    }


    public void testAntScript() throws Exception {
        String c1 =
            "package x;" +
            "public class C {" +
            "  private C() { }" +
            "}";
        createFile(1, "C.java", c1);
        String cc1 =
            "package x.ignore;" +
            "public class X {" +
            "  private X() { }" +
            "}";
        createFile(1, "X.java", cc1);
        String c2 =
            "package x;" +
            "public class C {" +
            "  public C() { }" +
            "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2);
    }

    public void testSubpackage() throws Exception {
        String c1 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "}";
        createFile(1, "C.java", c1);
        String cc1 =
            "package x.y.ignore;" +
            "public class X {" +
            "  private X() { }" +
            "}";
        createFile(1, "X.java", cc1);
        String c2 =
            "package x.y;" +
            "public class C {" +
            "  public C() { }" +
            "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=x.y.*");
    }

    public void testStaticMethodsReportedAsMissing() throws Exception {
        String c1 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "  public static C getDefault() { return new C(); }" +
            "}";
        createFile(1, "C.java", c1);
        String c2 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "  public static C getDefault() { return new C(); }" +
            "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=x.y.*", "-Dcheck.type=check");
    }

    public void testStaticFieldWasReportedAsMissing() throws Exception {
        String c1 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "  public static C DEFAULT = new C();" +
            "}";
        createFile(1, "C.java", c1);
        String c2 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "  public static C DEFAULT = new C();" +
            "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=x.y.*", "-Dcheck.type=check");
    }

    public void testUnion2Problem() throws Exception {
        String c1 =
            "package x.y;" +
            "public abstract class C<A,B> implements Cloneable {" +
            "  private C() { }" +
            "  @Override" +
            "  public abstract C<A,B> clone();" +
            "}";
        createFile(1, "C.java", c1);
        String c2 = c1;
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=x.y.*", "-Dcheck.type=check");
    }

    public void testNonStaticMethodsReportedAsMissing() throws Exception {
        String c1 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "  public C getDefault() { return new C(); }" +
            "}";
        createFile(1, "C.java", c1);
        String c2 =
            "package x.y;" +
            "public class C {" +
            "  private C() { }" +
            "  public C getDefault() { return new C(); }" +
            "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2, "-Dcheck.package=x.y.*", "-Dcheck.type=check");
    }

    public void testStrictCheckDiscoversAnAPIChange() throws Exception {
        String c1 =
            "package x;" +
            "public class C {" +
            "  private C() { }" +
            "}";
        createFile(1, "C.java", c1);
        String cc1 =
            "package x.ignore;" +
            "public class X {" +
            "  private X() { }" +
            "}";
        createFile(1, "X.java", cc1);
        String c2 =
            "package x;" +
            "public class C {" +
            "  public void newMeth() { }" +
            "}";
        createFile(2, "C.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.type=strictcheck");
            fail("This should fail, the mode is strict and we see some changes");
        } catch (ExecuteUtils.ExecutionError err) {
            // ok
        }
    }

    public void testStrictNestedInterfaces() throws Exception {
        String c1 = """
            package x;
            public interface P {
                    interface LB extends PI.PJ {
                    }
                    interface A extends P {
                    }
                    interface L extends LB {
                    }
                    class L_A implements L, A {
                    }
            }
            interface PI {
                    interface PJ extends P {
                    }
            }
            """;
        createFile(1, "P.java", c1);
        createFile(2, "P.java", c1);

        compareAPIs(1, 2, "-Dcheck.type=strictcheck");
    }

    public void testDeleteOfAMethodIsReported() throws Exception {
        String c1 =
            "package x;" +
            "public class C {" +
            "  public void x() { }" +
            "}";
        createFile(1, "C.java", c1);
        String c2 =
            "package x;" +
            "public class C {" +
            "  public C() { }" +
            "}";
        createFile(2, "C.java", c2);

        try {
            compareAPIs(1, 2, "-Dcheck.type=Check");
            fail("This comparition should fail");
        } catch (ExecuteUtils.ExecutionError err) {
            // ok
        }
    }

    public void testPackageInfoIsAnAPI() throws Exception {
        String c1 =
            "package x;" +
            "public class C {" +
            "  public void x() { }" +
            "}";
        createFile(1, "C.java", c1);
        String p1 =
            "@Deprecated\n" +
            "package x;\n";
        createFile(1, "package-info.java", p1);
        String c2 =
            "package x;" +
            "public class C {" +
            "  public void x() { }" +
            "}";
        createFile(2, "C.java", c2);

        compareAPIs(1, 2);
    }

    protected final void createFile(int slot, String name, String content) throws Exception {
        File d1 = new File(getWorkDir(), "dir" + slot);
        File c1 = new File(d1, name);
        copy(content, c1);
    }

    protected void compareAPIs(int slotFirst, int slotSecond, String... additionalArgs) throws Exception {
        File d1 = new File(getWorkDir(), "dir" + slotFirst);
        File d2 = new File(getWorkDir(), "dir" + slotSecond);

        File build = new File(getWorkDir(), buildScript());
        extractResource(buildScript(), build);

        List<String> args = new ArrayList<String>();
        args.addAll(Arrays.asList(additionalArgs));
        args.add("-Ddir1=" + d1);
        args.add("-Ddir2=" + d2);
        args.add("-Dcheck.release=" + checkRelease());
        args.add("-Dgenerate.release=" + generateRelease());
        ExecuteUtils.execute(build, args.toArray(new String[0]));
    }

    protected String checkRelease() {
        return "invalid-ignore";
    }

    protected String generateRelease() {
        return checkRelease();
    }

    protected String buildScript() {
        return "build.xml";
    }

    static final void copy(String txt, File f) throws Exception {
        f.getParentFile().mkdirs();
        FileWriter w = new FileWriter(f);
        w.append(txt);
        w.close();
    }

    final File extractResource(String res, File f) throws Exception {
        URL u = APITest.class.getResource(res);
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