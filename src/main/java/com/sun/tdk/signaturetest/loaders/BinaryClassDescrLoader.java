/*
 * $Id: BinaryClassDescrLoader.java 4516 2008-03-17 18:48:27Z eg216457 $
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

package com.sun.tdk.signaturetest.loaders;

import com.sun.tdk.signaturetest.SigTest;
import com.sun.tdk.signaturetest.classpath.Classpath;
import com.sun.tdk.signaturetest.core.ClassDescriptionLoader;
import com.sun.tdk.signaturetest.core.PrimitiveTypes;
import com.sun.tdk.signaturetest.model.*;
import com.sun.tdk.signaturetest.util.I18NResourceBundle;
import com.sun.tdk.signaturetest.util.LRUCache;

import java.io.*;
import java.util.*;


/**
 * This is subclass of the MemberCollectionBuilder provides searching class
 * files in the specified class path and loading ClassDescription
 * created via class file parsing. This class contains cache of the
 * parsed classes. This cache is changed using LRU algorithm.
 *
 * @author Maxim Sokolnikov
 * @author Roman Makarchuk
 */
public class BinaryClassDescrLoader implements ClassDescriptionLoader, LoadingHints {

    public static final boolean ANNOTATION_DEFAULT_VALUES_ON = true;

    private class BinaryClassDescription extends ClassDescription {

        private int major_version;          // class file format versions
        private int minor_version;

        private Constant[] constants;       // in-memory copy of the constant pool

        private String[] sigctors,
                sigfields,
                sigmethods;

        private void readCP(DataInput classData) throws IOException {
            int n = classData.readUnsignedShort();
            constants = new Constant[n];
            constants[0] = null;
            for (int i = 1; i < n; i++) {
                constants[i] = new Constant();
                constants[i].read(classData);
                if (constants[i].tag == CONSTANT_Long || constants[i].tag == CONSTANT_Double)
                    i++;
            }
        }

        List getMethodRefs() {
            ArrayList memberList = new ArrayList();
            int n = constants.length;
            for (int i = 1; i < n; i++) {
                if (constants[i].tag == CONSTANT_Long || constants[i].tag == CONSTANT_Double) {
                    i++;
                    continue;
                }
                if (constants[i].tag != CONSTANT_Methodref && constants[i].tag != CONSTANT_InterfaceMethodref
                        && constants[i].tag != CONSTANT_Fieldref) {
                    continue;
                }
                int refs = ((Integer) getConstant(i).info).intValue();
                short nameAndType = (short) refs;
                short decl = (short) (refs >> 16);


                String methodName = getMethodName(nameAndType);
                String className = getClassName(decl);
                boolean isConstructor = "<init>".equals(methodName);
                MemberDescription fid;

                if (constants[i].tag == CONSTANT_Fieldref) {
                    fid = new FieldDescr(methodName, className, 1);
                } else {
                    if (isConstructor) {
                        fid = new ConstructorDescr(this, 1);
                        fid.setDeclaringClass(className);
                    } else {
                        fid = new MethodDescr(methodName, className, 1);
                    }

                    String descr = getMethodType(nameAndType);
                    int pos = descr.indexOf(')');

                    try {
                        fid.setArgs(BinaryClassDescrLoader.getArgs(descr.substring(1, pos)));
                    } catch (IllegalArgumentException e) {
                        err(i18n.getString("BinaryClassDescrLoader.message.incorrectformat", Short.toString(decl)));
                    }
                }
                memberList.add(fid);
            }
            return memberList;
        }

        private Constant getConstant(int i) {
            if (i <= 0 || i >= constants.length)
                throw new ClassFormatError(i18n.getString("BinaryClassDescrLoader.error.cpoutofbounds"));

            return constants[i];
        }

        //  Read and store constant pool
        //

        String getClassName(int i) {
            if (i == 0)
                return null;

            Constant c = getConstant(i);
            c.checkConstant(CONSTANT_Class);

            c = getConstant(((Short) c.info).shortValue());
            c.checkConstant(CONSTANT_Utf8);

            return ((String) c.info).replace('/', '.');
        }

        private String getMethodName(int i) {
            if (i == 0)
                return null;

            Constant c = getConstant(i);
            c.checkConstant(CONSTANT_NameAndType);
            return getName(((Integer) c.info).intValue() >> 16);
        }

        private String getMethodType(int i) {
            if (i == 0)
                return null;
            Constant c = getConstant(i);
            c.checkConstant(CONSTANT_NameAndType);
            return getName(((Integer) c.info).shortValue());
        }

        private Object getConstantValue(int i) {
            Constant c = getConstant(i);
            if (c.tag == CONSTANT_String)
                return getName(((Short) c.info).intValue() & 0xFFFF);

            return c.info;
        }

        private String getName(int i) {
            Constant c = getConstant(i);
            c.checkConstant(CONSTANT_Utf8);

            return (String) c.info;
        }

        private void cleanup() {
            sigctors = null;
            sigfields = null;
            sigmethods = null;
            constants = null;
        }
    }

    private boolean ignoreAnnotations = false;

    /**
     * findByName and open class files as InputStream.
     */
    private Classpath classpath;

    /**
     * cache of the loaded classes.
     */
    private LRUCache cache;  //TODO if the cache is moved outside the loader, it became possible to clear ClassDescription.typeparamList

    /**
     * This stack is used to prevent infinite recursive calls of load(String name) method.
     * E.g. the annotation Documented is one example of such recursion
     */

    private Map stack = new HashMap();

    /**
     * creates new instance.
     *
     * @param classpath  contains class files.
     * @param bufferSize size of the class cache.
     */
    public BinaryClassDescrLoader(Classpath classpath, Integer bufferSize) {
        this.classpath = classpath;
        cache = new LRUCache(bufferSize.intValue());
    }

    /**
     * loads class with the given className
     *
     * @param className className of the class required to be found.
     */
    public ClassDescription load(String className) throws ClassNotFoundException {
        className = ExoticCharTools.decodeExotic(className);

        assert className.indexOf('<') == -1 : className;

        // search in the cache
        BinaryClassDescription c = (BinaryClassDescription) cache.get(className);

        if (c != null)
            return c;

        // check recursive call
        c = (BinaryClassDescription) stack.get(className);
        if (c != null)
            return c;

        // load class if the cache does not contains required class.
        try {
            c = new BinaryClassDescription();

            stack.put(className, c);
            readClass(c, classpath.findClass(className));
            cache.put(className, c);
        } catch (IOException e) {
            if (SigTest.debug)
                e.printStackTrace();
            throw new ClassNotFoundException(className);
        }
        finally {
            stack.remove(className);
        }

        return c;
    }


    private static I18NResourceBundle i18n = I18NResourceBundle.getBundleForClass(BinaryClassDescrLoader.class);


    // Magic number identifying class file format
    private static final int MAGIC = 0xCAFEBABE;

    private static final int TIGER_CLASS_VERSION = 49;
    private static final int J7_CLASS_VERSION = 51;

    //  Constant pool tags (see the JVM II 4.4, p. 103)
    private static final int
            CONSTANT_Class = 7,
            CONSTANT_Fieldref = 9,
            CONSTANT_Methodref = 10,
            CONSTANT_InterfaceMethodref = 11,
            CONSTANT_String = 8,
            CONSTANT_Integer = 3,
            CONSTANT_Float = 4,
            CONSTANT_Long = 5,
            CONSTANT_Double = 6,
            CONSTANT_NameAndType = 12,
            CONSTANT_Utf8 = 1,
            CONSTANT_MethodHandle = 15,
            CONSTANT_MethodType = 16,
            CONSTANT_InvokeDynamic = 18;

//  Constant pool entry

    private static class Constant {
        byte tag;
        Object info;

        void checkConstant(int exp) {
            if (tag != exp) {
                String[] consts = {Integer.toString(exp & 0xFF), Integer.toString(tag & 0xFF)};
                throw new ClassFormatError(i18n.getString("BinaryClassDescrLoader.error.const", consts));
            }
        }

        String getName() {
            checkConstant(CONSTANT_Utf8);
            return (String) info;
        }

        void read(DataInput classData) throws IOException {
            tag = classData.readByte();
            switch (tag) {
                case CONSTANT_Class:
                    info = new Short(classData.readShort());
                    break;

                case CONSTANT_Fieldref:
                case CONSTANT_Methodref:
                case CONSTANT_InterfaceMethodref:
                    info = new Integer(classData.readInt());
                    break;

                case CONSTANT_String:
                    info = new Short(classData.readShort());
                    break;

                case CONSTANT_Integer:
                    info = new Integer(classData.readInt());
                    break;

                case CONSTANT_Float:
                    info = new Float(classData.readFloat());
                    break;

                case CONSTANT_Long:
                    info = new Long(classData.readLong());
                    break;

                case CONSTANT_Double:
                    info = new Double(classData.readDouble());
                    break;

                case CONSTANT_NameAndType:
                    info = new Integer(classData.readInt());
                    break;

                case CONSTANT_Utf8:
                    info = classData.readUTF();
                    break;
                case CONSTANT_MethodHandle:
                    byte kind = classData.readByte();
                    short index = classData.readShort();
                    info = new Integer(kind << 16 | index);
                    break;
                case CONSTANT_MethodType:
                    info = new Short(classData.readShort());
                    break;
                case CONSTANT_InvokeDynamic:
                    info = new Integer(classData.readInt());
                    break;

                default:
                    throw new ClassFormatError(i18n.getString("BinaryClassDescrLoader.error.unknownconst",
                            Integer.toString(tag)));
            }
        }
    }

    private void readClass(BinaryClassDescription c, InputStream is) throws IOException {

        DataInput classData = new DataInputStream(is);

        try {
            readClass(c, classData);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        finally {
            c.cleanup();
            is.close();
        }
    }

    private void readClass(BinaryClassDescription c, DataInput classData) throws IOException {

        int magic = classData.readInt();
        if (magic != MAGIC) {
            String invargs[] = {Integer.toString(magic), Integer.toString(MAGIC)};
            throw new ClassFormatError(i18n.getString("BinaryClassDescrLoader.error.magicnum", invargs));
        }

        c.minor_version = classData.readUnsignedShort();
        c.major_version = classData.readUnsignedShort();

        c.setTiger(c.major_version >= TIGER_CLASS_VERSION);

        c.readCP(classData);

        int flags = classData.readUnsignedShort();
        c.setModifiers(flags);

        //  for nested class, name of the declaring class can be obtained from
        //  the 'InnerClasses' attribute only.
        String clName = c.getClassName(classData.readUnsignedShort());
        c.setupClassName(clName, MemberDescription.NO_DECLARING_CLASS);   // can be reassigned later

        String s = c.getClassName(classData.readUnsignedShort());
        if (s != null && (!c.hasModifier(Modifier.INTERFACE))) {
            SuperClass superClass = new SuperClass();
            superClass.setupClassName(s);
            c.setSuperClass(superClass);
        }

        String fqname = c.getQualifiedName();

        int n = classData.readUnsignedShort();
        c.createInterfaces(n);
        for (int i = 0; i < n; i++) {
            s = c.getClassName(classData.readUnsignedShort());
            SuperInterface fid = new SuperInterface();
            fid.setupGenericClassName(s);
            fid.setDirect(true);
            c.setInterface(i, fid);
        }

        readFields(c, classData);

        readMethods(c, classData);

        ClassAttrs attrs = new ClassAttrs();
        attrs.read(c, classData);

        c.setAnnoList(AnnotationItem.toArray(attrs.annolist));

        //  Generic-specific processing
        SignatureParser parser = null;

        if (c.major_version >= TIGER_CLASS_VERSION) {
            //  (JDK 1.5-b35) :
            //  workaround for class 'javax.crypto.SunJCE_c' with version 45:3
            //  and meaningless signature attribute.

            ClassDescription.TypeParameterList tp = null;
            String declaringClass = c.getDeclaringClassName();
            if (!MemberDescription.NO_DECLARING_CLASS.equals(declaringClass)) {

                boolean desclaringClassExists = false;
                try {
                    desclaringClassExists = classpath.findClass(declaringClass) != null;
                } catch (ClassNotFoundException e) {
                    c.setNoDeclaringClass();
                    warning(i18n.getString("BinaryClassDescrLoader.error.enclosing_class_not_found", c.getQualifiedName()));
                }

                if (desclaringClassExists) {
                    try {
                        tp = load(declaringClass).getTypeparamList();
                    }
                    catch (ClassNotFoundException e) {
                        throw new ClassFormatError(i18n.getString("BinaryClassDescrLoader.error.enclosing", fqname));
                    }
                }
            }

            ClassDescription.TypeParameterList typeparamList = new ClassDescription.TypeParameterList(tp);
            c.setTypeparamList(typeparamList);
            parser = new SignatureParser(fqname, typeparamList);
        }

        //  Process the 'Signature' attributes
        if (parser != null) {

            if (attrs.signature != null) {
                try {
                    parser.scanClass(attrs.signature);
                    c.setTypeParameters(parser.generic_pars);

                    SuperClass superClass = c.getSuperClass();
                    if (superClass != null)
                        superClass.setupGenericClassName(parser.class_supr);

                    int num = parser.class_intfs.size();
                    c.createInterfaces(num);
                    for (int i = 0; i < num; i++) {
                        SuperInterface fid = new SuperInterface();
                        fid.setupGenericClassName((String) parser.class_intfs.get(i));
                        fid.setDirect(true);
                        c.setInterface(i, fid);
                    }
                }
                catch (SigAttrError e) {
                    warning(e.getMessage());
                }
            }

            //  The 'Signature' attributes of fields, methods and constructors
            //  must be processed only after the corresponding class attribute

            postFields(c, parser);
            postMethods(c, parser);
        }
    }


    public List loadCalls(String name) throws ClassNotFoundException {

        // String name = ClassCorrector.stripGenerics(className);
        List result = new ArrayList();
        try {
            BinaryClassDescription c = new BinaryClassDescription();
            DataInputStream classData = new DataInputStream(classpath.findClass(name));
            try {
                readClass(c, (DataInput) classData);
                result = c.getMethodRefs();
            }
            finally {
                c.cleanup();
                classData.close();
            }
        } catch (IOException e) {
            if (SigTest.debug)
                e.printStackTrace();
            throw new ClassNotFoundException(name);
        }
        return result;
    }

    private class ClassAttrs extends AttrsIter {

        int access = -1;

        void check(BinaryClassDescription c, String s) throws IOException {
            if (s.equals("InnerClasses")) {
                List tmp = null;

                String fqname = c.getQualifiedName();

                int n = is.readUnsignedShort();
                for (int i = 0; i < n; i++) {
                    String inner = c.getClassName(is.readUnsignedShort());
                    String outer = c.getClassName(is.readUnsignedShort());
                    int k = is.readUnsignedShort();
//                    String simple = (k == 0 ? null : getName(k));
                    int x = is.readUnsignedShort();

                    if (inner != null && inner.equals(fqname)) {
                        if (access != -1)
                            err(null);
                        access = x;
                        c.setModifiers(x);
                        c.setupClassName(fqname, outer);
                    }

                    if (!hasHint(LoadingHints.READ_SYNTETHIC)) {
                        // skip synthetic inner classes
                        if (Modifier.hasModifier(x, Modifier.ACC_SYNTHETIC)) {
                            continue;
                        }
                    }

                    // Warning: javax.crypto.SunJCE_m reported as inner class of javax.crypto.Cipher
                    // so additional check inner.indexOf(outer) added!
                    if (outer != null && outer.equals(fqname) && inner.indexOf(outer) == 0) {
                        if (tmp == null)
                            tmp = new ArrayList();

                        tmp.add(new InnerDescr(inner, outer, x));
                    }
                }

                if (tmp != null)
                    c.setNestedClasses((InnerDescr[]) tmp.toArray(InnerDescr.EMPTY_ARRAY));
            }
        }

    }

//  Process fields


    private void readFields(BinaryClassDescription c, DataInput classData) throws IOException {

        int n = classData.readUnsignedShort();

        List flds = new ArrayList();
        List tmpflds = new ArrayList();

        for (int i = 0; i < n; i++) {

            int mod = classData.readUnsignedShort();
            FieldDescr fid = new FieldDescr(c.getName(classData.readUnsignedShort()), c.getQualifiedName(), mod);

            String type = convertVMType(c.getName(classData.readUnsignedShort()));
            fid.setType(type);

            FieldAttrs attrs = new FieldAttrs();
            attrs.read(c, classData);

            if (!hasHint(LoadingHints.READ_SYNTETHIC)) {
                if (fid.hasModifier(Modifier.ACC_SYNTHETIC)) {
                    if (SigTest.debug)
                        System.out.println(i18n.getString("BinaryClassDescrLoader.message.synthetic_field_skipped",
                                fid.getType() + " " + fid.getQualifiedName()));
                    continue;
                }
            }

            flds.add(fid);

            fid.setAnnoList(AnnotationItem.toArray(attrs.annolist));
            tmpflds.add(attrs.signature);

            if (fid.isFinal() && attrs.value != null) {
                if ("boolean".equals(type))
                    attrs.value = Boolean.valueOf(((Integer) attrs.value).intValue() != 0);

                else if ("byte".equals(type))
                    attrs.value = new Byte(((Integer) attrs.value).byteValue());

                else if ("char".equals(type))
                    attrs.value = new Character((char) ((Integer) attrs.value).shortValue());

                fid.setConstantValue(MemberDescription.valueToString(attrs.value));
            }
        }

        if ((n = flds.size()) != 0) {
            c.setFields((FieldDescr[]) flds.toArray(FieldDescr.EMPTY_ARRAY));
            c.sigfields = (String[]) tmpflds.toArray(EMPTY_STRING_ARRAY);
        }
    }


    private void postFields(BinaryClassDescription c, SignatureParser parser) {
        String sig;

        if (c.sigfields != null) {
            for (int i = 0; i < c.sigfields.length; i++)
                if ((sig = c.sigfields[i]) != null) {
                    try {
                        parser.scanField(sig);
                        c.getField(i).setType(parser.field_type);
                    }
                    catch (SigAttrError e) {
                        warning(e.getMessage());
                    }
                }
        }
    }

    class FieldAttrs extends AttrsIter {
        Object value = null;

        void check(BinaryClassDescription c, String s) throws IOException {
            if (s.equals("ConstantValue")) {
                if (value != null)
                    err(null);


                value = c.getConstantValue(is.readUnsignedShort());
            }
        }
    }

//  Process methods and constructors

    private static String[] EMPTY_STRING_ARRAY = new String[0];

    private void readMethods(BinaryClassDescription c, DataInput classData) throws IOException {
        List ctors = new ArrayList(),
                mthds = new ArrayList();

        List tmpctors = new ArrayList(),
                tmpmthds = new ArrayList();

        int n = classData.readUnsignedShort();
        for (int i = 0; i < n; i++) {

            int modif = classData.readUnsignedShort();
            String methodName = c.getName(classData.readUnsignedShort());


            boolean isConstructor = "<init>".equals(methodName);
            MemberDescription fid;

            if (isConstructor) {
                fid = new ConstructorDescr(c, modif);
            } else
                fid = new MethodDescr(methodName, c.getQualifiedName(), modif);

            boolean isBridgeMethod = fid.hasModifier(Modifier.BRIDGE);
            boolean isSynthetic = fid.hasModifier(Modifier.ACC_SYNTHETIC);
//            boolean isSyntheticConstuctor = isConstructor && fid.hasModifier(Modifier.ACC_SYNTHETIC);

            String descr = c.getName(classData.readUnsignedShort());
            int pos = descr.indexOf(')');

            try {
                fid.setArgs(getArgs(descr.substring(1, pos)));
            }
            catch (IllegalArgumentException e) {
                err(i18n.getString("BinaryClassDescrLoader.message.incorrectformat", c.getQualifiedName()));
            }

            fid.setType(convertVMType(descr.substring(pos + 1)));

            MethodAttrs attrs = new MethodAttrs();
            attrs.read(c, classData);

            // skip synthetic methods and constructors
            if (!hasHint(LoadingHints.READ_SYNTETHIC) && isSynthetic) {
                if (SigTest.debug) {
                    if (isConstructor) {
                        System.out.println(i18n.getString("BinaryClassDescrLoader.message.synthetic_constr_skipped",
                                fid.getQualifiedName() + "(" + fid.getArgs() + ")"));
                    } else {
                        String signature = fid.getType() + " " + fid.getQualifiedName() + "(" + fid.getArgs() + ")";
                        if (isBridgeMethod) {
                            System.out.println(i18n.getString("BinaryClassDescrLoader.message.bridge", signature));
                        } else {
                            System.out.println(i18n.getString("BinaryClassDescrLoader.message.synthetic_method_skipped",
                                    signature));
                        }
                    }
                }
                continue;
            }

            if (attrs.annodef != null) {
                fid.addModifier(Modifier.HASDEFAULT);
                ((MethodDescr) fid).setAnnoDef(attrs.annodef);
            }

            fid.setThrowables(MemberDescription.getThrows(attrs.xthrows));

            if (isConstructor) {
                fid.setType(MemberDescription.NO_TYPE);
                ctors.add(fid);
                tmpctors.add(attrs.signature);
            } else if ("<clinit>".equals(fid.getName())) {
                //  ignore
            } else {
                mthds.add(fid);
                tmpmthds.add(attrs.signature);
            }

            fid.setAnnoList(AnnotationItem.toArray(attrs.annolist));
        }

        if ((n = ctors.size()) != 0) {
            c.setConstructors((ConstructorDescr[]) ctors.toArray(ConstructorDescr.EMPTY_ARRAY));
            c.sigctors = (String[]) tmpctors.toArray(EMPTY_STRING_ARRAY);
        }

        if ((n = mthds.size()) != 0) {
            c.setMethods((MethodDescr[]) mthds.toArray(MethodDescr.EMPTY_ARRAY));
            c.sigmethods = (String[]) tmpmthds.toArray(EMPTY_STRING_ARRAY);
        }
    }


    private void postMethods(BinaryClassDescription cls, SignatureParser parser) {
        String sig;

        if (cls.sigctors != null)
            for (int i = 0; i < cls.sigctors.length; i++)
                if ((sig = cls.sigctors[i]) != null) {
                    try {
                        parser.scanMethod(sig);
                        ConstructorDescr c = cls.getConstructor(i);
                        postMethod(parser, c);
                        c.setType(MemberDescription.NO_TYPE);
                    }
                    catch (SigAttrError e) {
                        warning(e.getMessage());
                    }
                }

        if (cls.sigmethods != null)
            for (int i = 0; i < cls.sigmethods.length; i++)
                if ((sig = cls.sigmethods[i]) != null) {
                    try {
                        parser.scanMethod(sig);
                        postMethod(parser, cls.getMethod(i));
                    }
                    catch (SigAttrError e) {
                        warning(e.getMessage());
                    }
                }
    }


    private void postMethod(SignatureParser parser, MemberDescription fid) {
        fid.setTypeParameters(parser.generic_pars);
        fid.setType(parser.field_type);

        StringBuffer sb = new StringBuffer();

        if (parser.method_args != null && parser.method_args.size() != 0) {
            sb.append(parser.method_args.get(0));
            for (int i = 1; i < parser.method_args.size(); i++)
                sb.append(MemberDescription.ARGS_DELIMITER).append(parser.method_args.get(i));
        }

        fid.setArgs(sb.toString());

        sb.setLength(0);
        if (parser.method_throws != null && parser.method_throws.size() != 0) {
            sb.append(parser.method_throws.get(0));
            for (int i = 1; i < parser.method_throws.size(); i++)
                sb.append(MemberDescription.THROWS_DELIMITER).append(parser.method_throws.get(i));
            fid.setThrowables(sb.toString());
        }
    }


    //  Pack arguments the following way:
    //      (<a1>, ... <aN>)
    //  arguments are separated by ',' without any blanks, or
    //      ()
    //  if there are no arguments.
    //
    //  Note: this method has side-effect - its parameter (descr) gets changed.
    //
    private static String getArgs(String descr) throws IllegalArgumentException {

        if (descr.equals(""))
            return descr;

        int pos = 0;
        int lastPos = descr.length();

        String type;
        StringBuffer args = new StringBuffer();

        int dims = 0;

        while (pos < lastPos) {

            char ch = descr.charAt(pos);

            if (ch == 'L') {
                int delimPos = descr.indexOf(';', pos);
                if (delimPos == -1)
                    delimPos = lastPos;
                type = convertVMType(descr.substring(pos, delimPos + 1));
                pos = delimPos + 1;
            } else if (ch == '[') {
                dims++;
                pos++;
                continue;
            } else {
                type = PrimitiveTypes.getPrimitiveType(ch);
                pos++;
            }

            args.append(type);

            for (int i = 0; i < dims; ++i)
                args.append("[]");
            dims = 0;

            if (pos < lastPos)
                args.append(',');
        }

        return args.toString();
    }


    class MethodAttrs extends AttrsIter {
        String[] xthrows;

        void check(BinaryClassDescription c, String s) throws IOException {
            if (s.equals("Exceptions")) {
                int n = is.readUnsignedShort();
                xthrows = new String[n];
                for (int i = 0; i < n; i++)
                    xthrows[i] = c.getClassName(is.readUnsignedShort());
            }
        }
    }

    //TreeSet<Integer> ts = new TreeSet<Integer>();

//  Commons

    //  Utility class to help in attributes processing.
    //

    private abstract class AttrsIter {

        DataInputStream is;

        boolean synthetic = false,
                deprecated = false;
        String signature = null;

        List/*AnnotationItem*/ annolist = null;
        Object annodef = null;

        void read(BinaryClassDescription c, DataInput classData) throws IOException {
            int n = classData.readUnsignedShort();

            for (int i = 0; i < n; i++) {
                String name = c.getName(classData.readUnsignedShort());
                int count = classData.readInt();
                //System.out.println("  attribute: "+name+" count: "+count);

                if (count != 0) {
                    byte[] info = new byte[count];
                    classData.readFully(info);
                    is = new DataInputStream(new ByteArrayInputStream(info));
                }

                if (name.equals("Synthetic"))
                    synthetic = true;

                else if (name.equals("Deprecated"))
                    deprecated = true;

                else if (name.equals("Signature")) {
                    checkVersion(c, name, TIGER_CLASS_VERSION);
                    signature = c.getName(is.readUnsignedShort());
                } else if (SigTest.isTigerFeaturesTracked && name.equals("RuntimeVisibleAnnotations")) {
                    checkVersion(c, name, TIGER_CLASS_VERSION);
                    readAnnotations(c, 0);

                } else if (SigTest.isTigerFeaturesTracked && name.equals("RuntimeInvisibleAnnotations")) {
                    checkVersion(c, name, TIGER_CLASS_VERSION);
                    readAnnotations(c, 0);
                } else if (SigTest.isTigerFeaturesTracked && name.equals("RuntimeVisibleTypeAnnotations")) {
                    checkVersion(c, name, J7_CLASS_VERSION);
                    readExtAnnotations(c, 0);
                } else if (SigTest.isTigerFeaturesTracked && name.equals("RuntimeInvisibleTypeAnnotations")) {
                    checkVersion(c, name, J7_CLASS_VERSION);
                    readExtAnnotations(c, 0);
                } else if (SigTest.isTigerFeaturesTracked && 
                        (name.equals("RuntimeVisibleParameterAnnotations") || name.equals("RuntimeInvisibleParameterAnnotations")) ) {
                    checkVersion(c, name, TIGER_CLASS_VERSION);
                    int m = is.readUnsignedByte();
                    for (int l = 0; l < m; l++)
                        readAnnotations(c, l + 1);
                } else if (SigTest.isTigerFeaturesTracked && name.equals("AnnotationDefault")) {
                    checkVersion(c, name, TIGER_CLASS_VERSION);
                    annodef = read_member_value(c);
                } else {
                    check(c, name);
                }

                if (count != 0) {
                    try {
                        is.close();
                    }
                    catch (IOException x) {
                    }
                }
            }
        }


        void readAnnotations(BinaryClassDescription c, int target) throws IOException {
            if (ignoreAnnotations) {
                return;
            }
            if (annolist == null)
                annolist = new ArrayList();

            int m = is.readUnsignedShort();
            for (int l = 0; l < m; l++) {
                try {
                    AnnotationItem anno = readAnnotation(c, target, false);
                    annolist.add(anno);
                } catch (AnnotationNotFoundException ex) {
                    final String annoName = ex.getMessage();
                    if (!AnnotationItem.isInternal(annoName)) {
                        System.out.println("Warning: " + i18n.getString("BinaryClassDescrLoader.error.annotnotfound", annoName));
                    }
                }
            }
        }

        void readExtAnnotations(BinaryClassDescription c, int target) throws IOException {
            if (ignoreAnnotations) {
                return;
            }
            if (annolist == null) {
                annolist = new ArrayList();
            }
            boolean tracked = true;
            int m = is.readUnsignedShort();
            for (int l = 0; l < m; l++) {
                AnnotationItemEx anno = new AnnotationItemEx();
                int target_type = is.readUnsignedByte();

                anno.setTargetType(target_type);
                switch (target_type) {
                    case AnnotationItemEx.TARGET_METHOD_RECEIVER:
                        // empty, it's ok
                        break;
                    case AnnotationItemEx.TARGET_METHOD_RETURN_TYPE_GENERIC_ARRAY:
                    case AnnotationItemEx.TARGET_FIELD_GENERIC_ARRAY:
                        anno.setLocations(readLocations());
                        break;
                    case AnnotationItemEx.TARGET_METHOD_PARAMETER_GENERIC_ARRAY:
                        anno.setParameterIndex(readByte()).setLocations(readLocations());
                        break;
                    case AnnotationItemEx.TARGET_CLASS_TYPE_PARAMETER_BOUND:
                    case AnnotationItemEx.TARGET_METHOD_TYPE_PARAMETER_BOUND:
                        anno.setParameterIndex(readByte()).setBoundIndex(readByte());
                        break;
                    case AnnotationItemEx.TARGET_CLASS_TYPE_PARAMETER_BOUND_GENERIC_ARRAY:
                    case AnnotationItemEx.TARGET_METHOD_TYPE_PARAMETER_BOUND_GENERIC_ARRAY:
                        anno.setParameterIndex(readByte()).setBoundIndex(readByte()).setLocations(readLocations());
                        break;
                    case AnnotationItemEx.TARGET_CLASS_EXTENDS_IMPLEMENTS:
                    case AnnotationItemEx.TARGET_EXCEPTION_TYPE_IN_THROWS:
                        anno.setTypeIndex(readByte());
                        break;
                    case AnnotationItemEx.TARGET_CLASS_EXTENDS_IMPLEMENTS_GENERIC_ARRAY:
                        anno.setTypeIndex(readByte()).setLocations(readLocations());
                        break;
                    case AnnotationItemEx.TARGET_WILDCARD_BOUND:
                        throw new IllegalStateException("TARGET_WILDCARD_BOUND is not supported yet");
//                         break;
                    case AnnotationItemEx.TARGET_WILDCARD_BOUND_GENERIC_ARRAY:
                        throw new IllegalStateException("TARGET_WILDCARD_BOUND_GENERIC_ARRAY is not supported yet");
//                        break;
                    case AnnotationItemEx.TARGET_METHOD_TYPE_PARAMETER:
                    case AnnotationItemEx.TARGET_CLASS_TYPE_PARAMETER:
                        anno.setParameterIndex(readByte());
                        break;
                        // --- BODY's annotations - just skip them ---
                    case AnnotationItemEx.TARGET_TYPECAST:
                    case AnnotationItemEx.TARGET_TYPE_TEST:
                    case AnnotationItemEx.TARGET_OBJECT_CREATION:
                    case AnnotationItemEx.TARGET_CLASS_LITERAL:
                        is.readUnsignedShort();
                        tracked = false;
                        break;
                    case AnnotationItemEx.TARGET_TYPECAST_GENERIC_ARRAY:
                    case AnnotationItemEx.TARGET_TYPE_TEST_GENERIC_ARRAY:
                    case AnnotationItemEx.TARGET_OBJECT_CREATION_GENERIC_ARRAY:
                        is.readUnsignedShort();
                        readLocations();
                        tracked = false;
                        break;
                    case AnnotationItemEx.TARGET_LOCAL_VARIABLE: 
                        int table_length = is.readUnsignedShort();
                        for (int i = 0; i < table_length; i++) {
                            is.readUnsignedShort();
                            is.readUnsignedShort();
                            is.readUnsignedShort();
                        }
                        tracked = false;
                        break;
                    case AnnotationItemEx.TARGET_LOCAL_VARIABLE_GENERIC_ARRAY:
                        table_length = is.readUnsignedShort();
                        for (int i = 0; i < table_length; i++) {
                            is.readUnsignedShort();
                            is.readUnsignedShort();
                            is.readUnsignedShort();
                        }
                        readLocations();
                        tracked = false;
                        break;
                    case AnnotationItemEx.TARGET_TYPE_ARGUMENT_IN_CONSTRUCTOR_CALL:
                    case AnnotationItemEx.TARGET_TYPE_ARGUMENT_IN_METHOD_CALL:
                        is.readUnsignedShort();
                        is.readUnsignedByte();
                        tracked = false;
                        break;
                    case AnnotationItemEx.TARGET_TYPE_ARGUMENT_IN_CONSTRUCTOR_CALL_GENERIC_ARRAY:
                    case AnnotationItemEx.TARGET_TYPE_ARGUMENT_IN_METHOD_CALL_GENERIC_ARRAY:
                        is.readUnsignedShort();
                        is.readUnsignedByte();
                        readLocations();
                        tracked = false;
                        break;
                    case AnnotationItemEx.TARGET_CLASS_LITERAL_GENERIC_ARRAY:
                        is.readUnsignedShort();
                        readLocations();
                        tracked = false;
                        break;
                    default:
                        throw new IllegalStateException("Unknown type 0x" + Integer.toHexString(target_type));
                }
                if (tracked || hasHint(LoadingHints.READ_ANY_ANNOTATIONS)) {
                    annolist.add(anno);
                }
            }
        }

        private int readByte() throws IOException {
            int parameter_index = is.readUnsignedByte();
            return parameter_index;
        }

        private int[] readLocations() throws IOException {
            int location_length = is.readUnsignedShort();
            int [] loc = new int [location_length];
            for (int i = 0; i < location_length; i++) {
                loc[i] = is.readUnsignedByte();
            }
            return loc;
        }

        private AnnotationItem readAnnotation(BinaryClassDescription c, int target, boolean isExtended) throws IOException {
            AnnotationItem anno;
            if (isExtended) {
                anno = new AnnotationItemEx(target, convertVMType(c.getName(is.readUnsignedShort())));
            } else {
                anno = new AnnotationItem(target, convertVMType(c.getName(is.readUnsignedShort())));
            }

            int k = is.readUnsignedShort();
            for (int j = 0; j < k; j++)
                anno.addMember(new AnnotationItem.Member(c.getName(is.readUnsignedShort()),
                        read_member_value(c)));

            completeAnnotation(anno);
            return anno;
        }

        void completeAnnotation(AnnotationItem anno) throws AnnotationNotFoundException {
            try {
                ClassDescription c = load(anno.getName());

                AnnotationItem[] annoList = c.getAnnoList();

                for (int k = 0; k < annoList.length; k++)
                    if ("java.lang.annotation.Inherited".equals(annoList[k].getName()))
                        anno.setInheritable(true);

                MethodDescr[] fids = c.getDeclaredMethods();
                if (fids != null)
                    for (int i = 0; i < fids.length; i++) {
                        MethodDescr fid = fids[i];
                        AnnotationItem.Member member = anno.findByName(fid.getName());
                        if (member != null) {
                            anno.removeMember(member);
                            member.setType(fid.getType());
                        } else {
                            member = new AnnotationItem.Member(fid.getType(), fid.getName(), fid.getAnnoDef());
                        }
                        anno.addMember(member);
                    }
            }
            catch (ClassNotFoundException e) {
                throw new AnnotationNotFoundException(anno.getName(), e);
            }
        }


        Object read_member_value(BinaryClassDescription c) throws IOException {
            Object v;
            byte tag = is.readByte();
            switch (tag) {
                case'Z':
                    v = c.getConstantValue(is.readUnsignedShort());
                    v = Boolean.valueOf(((Integer) v).intValue() != 0);
                    break;

                case'B':
                    v = c.getConstantValue(is.readUnsignedShort());
                    v = new Byte(((Integer) v).byteValue());
                    break;

                case'C':
                    v = c.getConstantValue(is.readUnsignedShort());
                    v = new Character((char) ((Integer) v).shortValue());
                    break;


                case'D':
                case'F':
                case'I':
                case'J':
                case'S':
                case's':
                    v = c.getConstantValue(is.readUnsignedShort());
                    break;

                case'e': {
                    // Not used in fact
//                    String s = getName(is.readUnsignedShort());
                    is.readUnsignedShort();
                    v = new AnnotationItem.ValueWrap(c.getName(is.readUnsignedShort()));
                    break;
                }

                case'c': {
                    String s = convertVMType(c.getName(is.readUnsignedShort()));
                    v = new AnnotationItem.ValueWrap("class " + s);
                    break;
                }

                case'@':
                    v = readAnnotation(c, 0, false);
                    break;

                case'[': {
                    int n = is.readUnsignedShort();
                    Object[] tmp = new Object[n];
                    for (int i = 0; i < n; i++)
                        tmp[i] = read_member_value(c);
                    v = tmp;
                    break;
                }

                default:
                    throw new ClassFormatError(i18n.getString("BinaryClassDescrLoader.error.unknownannot",
                            Integer.toString(tag)));
            }

            return v;
        }


        void checkVersion(BinaryClassDescription c, String name, int vnbr) {
            String[] args = {name, c.getQualifiedName(), Integer.toString(c.major_version), Integer.toString(c.minor_version)};
            if (c.major_version < vnbr)
                getLog().println(i18n.getString("BinaryClassDescrLoader.message.attribute", args));
        }


        //  Process attribute with the name given.
        //  Attribute data can be read using the 'is' stream.
        //
        abstract void check(BinaryClassDescription c, String name) throws IOException;

    } //end of abstract class AttrsIter

//    private Constant getConstant(short I) {
//        return getConstant(((int) I) & 0xFFFF);
//    }

    //  Convert JVM type notation (as described in the JVM II 4.3.2, p.100)
    //  to JLS type notation :
    //
    //      [<s>  -> <s>[]      <s> is converted recursively
    //      L<s>; -> <s>        characters '/' are replaced by '.' in <s>
    //      B     -> byte
    //      C     -> char
    //      D     -> double
    //      F     -> float
    //      I     -> int
    //      J     -> long
    //      S     -> short
    //      Z     -> boolean
    //      V     -> void       valid only in method return type
    //
    //

    static String convertVMType(String s) {
        return MemberDescription.getTypeName(s.replace('/', '.'));
    }

    static class SignatureParser {
        //  Parse results area -

        String generic_pars;

        String class_supr;
        List/*String*/ class_intfs;

        String field_type;
        List/*String*/ method_args,
                method_throws;

        //  - end of results area

        final String ownname;
        final ClassDescription.TypeParameterList typeparams;

        String sig;
        int siglength;
        char chr;
        int idx;


        SignatureParser(String name, final ClassDescription.TypeParameterList pars) {
            ownname = name;
            typeparams = pars;
        }

        void scanClass(String s) {
            //System.out.println("scanClass \"" + s + "\"" );

            sig = s + "\n";
            siglength = sig.length();
            idx = 0;
            chr = sig.charAt(idx++);

            generic_pars = scanFormalTypeParameters(ownname);
            class_supr = scanFieldTypeSignature(true);

            class_intfs = new ArrayList();
            while (!eol()) {
                String intf = scanFieldTypeSignature(true);
                class_intfs.add(intf);
            }
        }

        void scanField(String s) {
            //System.out.println("scanField \"" + s + "\"" );

            sig = s + "\n";
            siglength = sig.length();
            idx = 0;
            chr = sig.charAt(idx++);

            field_type = scanFieldTypeSignature(true);
        }


        void scanMethod(String s) {
            //System.out.println("scanMethod  \"" + s + "\"" );

            sig = s + "\n";
            siglength = sig.length();
            idx = 0;
            chr = sig.charAt(idx++);

            generic_pars = scanFormalTypeParameters("%");

            method_args = new ArrayList();
            scanChar('(');
            while (chr != ')') {
                String t = scanFieldTypeSignature(true);
                method_args.add(t);
            }
            scanChar(')');

            field_type = scanFieldTypeSignature(true);

            method_throws = new ArrayList();
            while (chr == '^') {
                scanChar();
                String t = scanFieldTypeSignature(true);
                method_throws.add(t);
            }

            typeparams.clear("%");
        }


        String scanFormalTypeParameters(String declared) {
            if (chr != '<')
                return null;

            List/*List*/ parameters = new ArrayList();

            //  First pass:scan all parameters and store bounds because they may
            //  contain forward references

            typeparams.reset_count();
            scanChar('<');
            for (; ;) {
                String ident = scanIdent(":>");
                List/*String*/ bounds = new ArrayList();

                while (chr == ':') {
                    scanChar();

                    //  first bound (class bound) can be omitted
                    String bound = null;
                    if (chr != ':')
                        bound = scanFieldTypeSignature(false);

                    if (bound != null)
                        bounds.add(bound);
                }

                parameters.add(bounds);
                typeparams.add(ident, declared);

                if (chr == '>')
                    break;
            }
            scanChar('>');

            //  Second pass: findByName and replace possible forward links and sort bounds

            StringBuffer sb = new StringBuffer();
            sb.append('<');
            for (int i = 0; i < parameters.size(); i++) {
                if (i != 0)
                    sb.append(", ");

                //  replace type variable with its ordinal number
                sb.append('%').append(String.valueOf(i));

                List bounds = (List) parameters.get(i);

                //  replace possible forward refs '{ident}'
                for (int k = 0; k < bounds.size(); k++)
                    bounds.set(k, typeparams.replaceForwards((String) bounds.get(k)));

                //  first bound is erasure and must stay in place
                //  remaining bounds (if any) are sorted
                if (bounds.size() > 0) {
                    String first = (String) bounds.remove(0);
                    sb.append(" extends ").append(first);

                    if (bounds.size() != 0) {
                        Collections.sort(bounds);
                        for (int k = 0; k < bounds.size(); k++)
                            sb.append(" & ").append((String) bounds.get(k));
                    }
                }
            }
            sb.append('>');
            return sb.toString();
        }


        String scanFieldTypeSignature(boolean repl) {
            switch (chr) {
                case'[':
                    scanChar();
                    return scanFieldTypeSignature(repl) + "[]";

                case'L': {
                    scanChar();
                    StringBuffer sb = new StringBuffer();
                    StringBuffer sb1 = new StringBuffer();

                    for (; ;) {
                        sb.append(scanIdent("<.;").replace('/', '.'));

                        if (chr == '<') {
                            scanChar();
                            sb1.append('<');

                            sb1.append(scanTypeArgument(repl));

                            while (chr != '>') sb1.append(',').append(scanTypeArgument(repl));

                            scanChar('>');
                            sb1.append('>');
                        }

                        if (chr != '.') {
                            if (sb1.length() != 0)
                                sb.append(sb1);
                            break;
                        }
                        sb1.setLength(0);

                        scanChar();
                        sb.append('$');
                    }

                    scanChar(';');
                    return sb.toString();
                }

                case'T': {
                    scanChar();
                    String ident = scanIdent(";");
                    scanChar(';');
                    return repl ? typeparams.replace(ident) : ClassDescription.TypeParameterList.replaceNone(ident);
                }

                default: {
                    String t = PrimitiveTypes.getPrimitiveType(chr);
                    if (t != null) {
                        scanChar();
                        return t;
                    }

                    err("?TypeChar " + String.valueOf(chr));
                    return null;
                }
            }
        }


        String scanTypeArgument(boolean repl) {

            final String object = "java.lang.Object";

            switch (chr) {
                case'*':
                    scanChar();
                    return "?";

                case'+': {
                    scanChar();

                    String s = scanFieldTypeSignature(repl);
                    //  Reduce "? extends java.lang.Object" to just "?"
                    if (s.startsWith(object))
                        s = s.substring(object.length()).trim();

                    return (s.length() > 0) ? "? extends " + s : "?";
                }

                case'-':
                    scanChar();
                    return "? super " + scanFieldTypeSignature(repl);

                default:
                    return scanFieldTypeSignature(repl);
            }
        }


        String scanIdent(final String term) {
            StringBuffer sb = new StringBuffer();

            while (term.indexOf(chr) == -1) {
                sb.append(chr);
                scanChar();
            }

            if (sb.length() == 0)
                err(null);

            return sb.toString();
        }


        char scanChar(char c) {
            if (chr != c)
                err(null);

            return scanChar();
        }


        char scanChar() {
            if (idx >= siglength)
                err(null);

            return chr = sig.charAt(idx++);
        }


        boolean eol() {
            return idx >= siglength;
        }


        void err(String msg) throws Error {
            throw new SigAttrError(showerr() + (msg == null ? "" : "\n" + msg));
        }


        String showerr() {
            String[] args = {sig.substring(0, siglength - 1), ownname};
            return i18n.getString("BinaryClassDescrLoader.error.attribute", args);
        }
    }


    private void err(String s) {
        throw new ClassFormatError(s == null ? "???" : s);
    }


    private static class SigAttrError extends Error {
        SigAttrError(String msg) {
            super(msg);
        }
    }


    public void warning(String msg) {
        getLog().println(msg);
    }

    public void setIgnoreAnnotations(boolean value) {
        ignoreAnnotations = value;
    }

    private HashSet hints = new HashSet();

    public void addLoadingHint(Hint hint) {
        hints.add(hint);
    }

    private boolean hasHint(Hint hint) {
        return hints.contains(hint);
    }

    private PrintWriter getLog() {
        return log;
    }

    public void setLog(PrintWriter log) {
        this.log = log;
    }
    
    private PrintWriter log;

    private static final class AnnotationNotFoundException extends IOException {
        public AnnotationNotFoundException(String message, ClassNotFoundException cause) {
            super(message, cause);
        }

        @Override
        public ClassNotFoundException getCause() {
            return (ClassNotFoundException) super.getCause();
        }
    }
}
