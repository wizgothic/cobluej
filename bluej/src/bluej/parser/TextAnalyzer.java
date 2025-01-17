/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.parser;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bluej.debugger.gentype.BadInheritanceChainException;
import bluej.debugger.gentype.GenTypeArray;
import bluej.debugger.gentype.GenTypeCapture;
import bluej.debugger.gentype.GenTypeClass;
import bluej.debugger.gentype.GenTypeDeclTpar;
import bluej.debugger.gentype.GenTypeParameter;
import bluej.debugger.gentype.GenTypeSolid;
import bluej.debugger.gentype.GenTypeTpar;
import bluej.debugger.gentype.GenTypeWildcard;
import bluej.debugger.gentype.IntersectionType;
import bluej.debugger.gentype.JavaPrimitiveType;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.MethodReflective;
import bluej.debugger.gentype.Reflective;
import bluej.debugmgr.NamedValue;
import bluej.debugmgr.ValueCollection;
import bluej.debugmgr.texteval.DeclaredVar;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.PackageEntity;
import bluej.parser.entity.PackageOrClass;
import bluej.parser.entity.TypeEntity;
import bluej.parser.entity.ValueEntity;
import bluej.utility.JavaReflective;
import bluej.utility.JavaUtils;

/**
 * Parsing routines for the code pad.<p>
 * 
 * This is pretty tricky stuff, we try to following the Java Language Specification
 * (JLS) where possible.
 *  
 * @author Davin McCall
 */
public class TextAnalyzer
{
    //private ClassLoader classLoader;
    private EntityResolver parentResolver;
    private String packageScope;  // evaluation package
    private ValueCollection objectBench;

    private static JavaUtils jutils = JavaUtils.getJavaUtils();
    //private static boolean java15 = Config.isJava15();
    
    private List<DeclaredVar> declVars; // variables declared in the parsed statement block
    private String amendedCommand;  // command string amended with initializations for
                                    // all variables
    
    private ImportsCollection imports;
    private String importCandidate; // any import candidates.
    //private JavaRecognizer parser;
    
    /**
     * TextParser constructor. Defines the class loader and package scope
     * for evaluation.
     */
    public TextAnalyzer(EntityResolver parentResolver, String packageScope, ValueCollection ob)
    {
        this.parentResolver = parentResolver;
        this.packageScope = packageScope;
        this.objectBench = ob;
        imports = new ImportsCollection();
    }
    
    /**
     * Set a new class loader, and clear the imports list.
     */
    public void newClassLoader(ClassLoader newLoader)
    {
        //classLoader = newLoader;
        imports.clear();
    }
    
    /**
     * Parse a string entered into the code pad. Return is null if the string
     * is a statement; otherwise the string is an expression and the returned
     * string if the type of the expression (empty if the type cannot be determined).
     * 
     * <p>After calling this method, getDeclaredVars() and getAmendedCommand() can be
     * called - see the documentation for those methods respectively.
     * 
     * <p>If the parsed string is then executed, the confirmCommand() method should
     * subsequently be called.
     */
    public String parseCommand(String command)
    {
        importCandidate = "";
        amendedCommand = command;
        declVars = Collections.emptyList();
        //AST rootAST;
        
        EntityResolver resolver = getResolver(); 
        
        TextParser parser = new TextParser(resolver, command);
        
        // check if it's an import statement
        try {
            parser.parseImportStatement();
            if (parser.atEnd()) {
                amendedCommand = "";
                importCandidate = command;
                return null;
            }
        }
        catch (Exception e) {}
        
        CodepadVarParser vparser = new CodepadVarParser(resolver, command);
        try {
            if (vparser.parseVariableDeclarations() != null) {
                declVars = vparser.getVariables();
                if (! declVars.isEmpty()) {
                    for (DeclaredVar var : declVars) {
                        if (! var.isInitialized() && ! var.isFinal()) {
                            amendedCommand += "\n" + var.getName();
                            String text;
                            JavaType declVarType = var.getDeclaredType();
                            if (declVarType.isPrimitive()) {
                                if (declVarType.isNumeric()) {
                                    text = " = 0";
                                }
                                else {
                                    text = " = false";
                                }
                            }
                            else {
                                // reference type
                                text = " = null";
                            }
                            amendedCommand += text + ";\n";
                        }
                    }
                    return null; // not an expression
                }
            }
        }
        catch (Exception e) {}
        
        // Check if it's an expression
        parser = new TextParser(resolver, command);
        try {
            parser.parseExpression();
            if (parser.atEnd()) {
                JavaEntity exprType = parser.getExpressionType();
                if (exprType == null) {
                    return "";
                }
                else {
                    return exprType.resolveAsValue().getType().toString();
                }
            }
        }
        catch (Exception e) {}

        return null;
    }
    
    private EntityResolver getResolver()
    {
        EntityResolver resolver = new EntityResolver()
        {
            public TypeEntity resolveQualifiedClass(String name)
            {
                return parentResolver.resolveQualifiedClass(name);
            }
            
            public PackageOrClass resolvePackageOrClass(String name, Reflective querySource)
            {
                String pkgScopePrefix = packageScope;
                if (packageScope.length() > 0) {
                    pkgScopePrefix += ".";
                }

                // Imported class?
                TypeEntity entity = imports.getTypeImport(name);
                if (entity != null)
                {
                    return entity;
                }
                
                // Might be a class in the current package
                TypeEntity rval = parentResolver.resolveQualifiedClass(pkgScopePrefix + name);
                if (rval != null) {
                    return rval;
                }
                
                // Try in java.lang (see JLS 7.5.5)
                rval = parentResolver.resolveQualifiedClass("java.lang." + name);
                if (rval != null) {
                    return rval;
                }
                
                // Try in wildcard imports
                entity = imports.getTypeImportWC(name);
                if (entity != null) {
                    return entity;
                }
                
                // Have to assume it's a package
                return new PackageEntity(name, this);
            }
            
            public JavaEntity getValueEntity(String name, Reflective querySource)
            {
                NamedValue obVal = objectBench.getNamedValue(name);
                if (obVal != null) {
                    return new ValueEntity(obVal.getGenType());
                }
                List<JavaEntity> importStaticVals = imports.getStaticImports(name);
                if (importStaticVals != null && !importStaticVals.isEmpty()) {
                    return importStaticVals.get(0).getSubentity(name, querySource);
                }
                importStaticVals = imports.getStaticWildcardImports();
                if (importStaticVals != null) {
                    for (JavaEntity importStatic : importStaticVals) {
                        importStatic = importStatic.resolveAsType();
                        if (importStatic == null) {
                            continue;
                        }
                        JavaEntity entity = importStatic.getSubentity(name, querySource);
                        if (entity != null) {
                            return entity;
                        }
                    }
                }
                
                return resolvePackageOrClass(name, querySource);
            }
        };
        return resolver;
    }
    
    /**
     * Called to confirm that the recently parsed command has successfully
     * executed. This allows TextParser to update internal state to reflect
     * changes caused by the execution of the command.
     */
    public void confirmCommand()
    {
        if (importCandidate.length() != 0) {
            Reader r = new StringReader(importCandidate);
            CodepadImportParser parser = new CodepadImportParser(getResolver(), r);
            parser.parseImportStatement();
            if (parser.isStaticImport()) {
                if (parser.isWildcardImport()) {
                    imports.addStaticWildcardImport(parser.getImportEntity()
                            .resolveAsType());
                }
                else {
                    imports.addStaticImport(parser.getMemberName(),
                            parser.getImportEntity().resolveAsType());
                }
            }
            else {
                if (parser.isWildcardImport()) {
                    imports.addWildcardImport(parser.getImportEntity().resolveAsPackageOrClass());
                }
                else {
                    JavaEntity importEntity = parser.getImportEntity();
                    TypeEntity classEnt = importEntity.resolveAsType();
                    String name = classEnt.getType().toString(true);
                    imports.addNormalImport(name, classEnt);
                }
            }
        }
    }
    
    /**
     * Get a list of the variables declared in the recently parsed statement
     * block. The return is a List of TextParser.DeclaredVar
     */
    public List<DeclaredVar> getDeclaredVars()
    {
        return declVars;
    }
    
    /**
     * Get the amended command string, which has initializers inserted for variable
     * declarations which were missing initializers.
     */
    public String getAmendedCommand()
    {
        return amendedCommand;
    }
    
    /**
     * Return the imports collection as a sequence of java import statements.
     */
    public String getImportStatements()
    {
        return imports.toString() + importCandidate;
    }
    
    /**
     * Java 1.4 & prior version of trinary "? :" operator. See JLS 2nd ed. 
     * section 15.25.
     * 
     * @throws RecognitionException
     * @throws SemanticException
     */
//    private JavaType questionOperator14(AST node) throws RecognitionException, SemanticException
//    {
//        AST trueAlt = node.getFirstChild().getNextSibling();
//        AST falseAlt = trueAlt.getNextSibling();
//        ExprValue trueAltEv = getExpressionType(trueAlt);
//        ExprValue falseAltEv = getExpressionType(falseAlt);
//        JavaType trueAltType = trueAltEv.getType();
//        JavaType falseAltType = falseAltEv.getType();
//        
//        // if the operands have the same type, that is the result type
//        if (trueAltType.equals(falseAltType))
//            return trueAltType;
//        
//        if (trueAltType.isNumeric() && falseAltType.isNumeric()) {
//            // if one type is short and the other is byte, result type is short
//            if (trueAltType.typeIs(JavaType.JT_SHORT) && falseAltType.typeIs(JavaType.JT_BYTE))
//                return JavaPrimitiveType.getShort();
//            if (falseAltType.typeIs(JavaType.JT_SHORT) && trueAltType.typeIs(JavaType.JT_BYTE))
//                return JavaPrimitiveType.getShort();
//            
//            // if one type is byte/short/char and the other is a constant of
//            // type int whose value fits, the result type is byte/short/char
//            if (falseAltType.typeIs(JavaType.JT_INT) && falseAltEv.knownValue()) {
//                int fval = falseAltEv.intValue();
//                if (isMinorInteger(trueAltType) && trueAltType.couldHold(fval))
//                    return trueAltType;
//            }
//            if (trueAltType.typeIs(JavaType.JT_INT) && trueAltEv.knownValue()) {
//                int fval = trueAltEv.intValue();
//                if (isMinorInteger(falseAltType) && falseAltType.couldHold(fval))
//                    return falseAltType;
//            }
//            
//            // binary numeric promotion is applied
//            return binaryNumericPromotion(trueAltType, falseAltType);
//        }
//        
//        // otherwise it must be possible to convert one type to the other by
//        // assignment conversion:
//        if (trueAltType.isAssignableFrom(falseAltType))
//            return trueAltType;
//        if (falseAltType.isAssignableFrom(trueAltType))
//            return falseAltType;
//        
//        throw new SemanticException();
//    }
    
    /**
     * Test if a given type is one of the "minor" integral types: byte, char
     * or short.
     */
//    private static boolean isMinorInteger(JavaType a)
//    {
//        return a.typeIs(JavaType.JT_BYTE) || a.typeIs(JavaType.JT_CHAR) || a.typeIs(JavaType.JT_SHORT); 
//    }
    
    /**
     * Java 1.5 version of the trinary "? :" operator.
     * See JLS section 15.25. Note that JLS 3rd ed. differs extensively
     * from JLS 2nd edition. The changes are not backwards compatible.
     * 
     * @throws RecognitionException
     * @throws SemanticException
     */
//    private JavaType questionOperator15(AST node) throws RecognitionException, SemanticException
//    {
//        AST trueAlt = node.getFirstChild().getNextSibling();
//        AST falseAlt = trueAlt.getNextSibling();
//        ExprValue trueAltEv = getExpressionType(trueAlt);
//        ExprValue falseAltEv = getExpressionType(falseAlt);
//        JavaType trueAltType = trueAltEv.getType();
//        JavaType falseAltType = falseAltEv.getType();
//        
//        // if we don't know the type of both alternatives, we don't
//        // know the result type:
//        if (trueAltType == null || falseAltType == null)
//            return null;
//        
//        // Neither argument can be a void type.
//        if (trueAltType.isVoid() || falseAltType.isVoid())
//            throw new SemanticException();
//        
//        // if the second & third arguments have the same type, then
//        // that is the result type:
//        if (trueAltType.equals(falseAltType))
//            return trueAltType;
//        
//        JavaType trueUnboxed = unBox(trueAltType);
//        JavaType falseUnboxed = unBox(falseAltType);
//        
//        // if one the arguments is of type boolean and the other
//        // Boolean, the result type is boolean.
//        if (trueUnboxed.typeIs(JavaType.JT_BOOLEAN) && falseUnboxed.typeIs(JavaType.JT_BOOLEAN))
//            return trueUnboxed;
//        
//        // if one type is null and the other is a reference type, the
//        // return is that reference type.
//        //   Also partially handle the final case from the JLS,
//        // involving boxing conversion & capture conversion (which
//        // is trivial when non-parameterized types such as boxed types
//        // are involved)
//        // 
//        // This precludes either type from being null later on.
//        if (trueAltType.typeIs(JavaType.JT_NULL))
//            return boxType(falseAltType);
//        if (falseAltType.typeIs(JavaType.JT_NULL))
//            return boxType(trueAltType);
//        
//        // if the two alternatives are convertible to numeric types,
//        // there are several cases:
//        if (trueUnboxed.isNumeric() && falseUnboxed.isNumeric()) {
//            // If one is byte/Byte and the other is short/Short, the
//            // result type is short.
//            if (trueUnboxed.typeIs(JavaType.JT_BYTE) && falseUnboxed.typeIs(JavaType.JT_SHORT))
//                return falseUnboxed;
//            if (falseUnboxed.typeIs(JavaType.JT_BYTE) && trueUnboxed.typeIs(JavaType.JT_SHORT))
//                return trueUnboxed;
//            
//            // If one type, when unboxed, is byte/short/char, and the
//            // other is an integer constant whose value fits in the
//            // first, the result type is the (unboxed) former type. (The JLS
//            // takes four paragraphs to say this, but the result is the
//            // same).
//            if (isMinorInteger(trueUnboxed) && falseAltType.typeIs(JavaType.JT_INT) && falseAltEv.knownValue()) {
//                int kval = falseAltEv.intValue();
//                if (trueUnboxed.couldHold(kval))
//                    return trueUnboxed;
//            }
//            if (isMinorInteger(falseUnboxed) && trueAltType.typeIs(JavaType.JT_INT) && trueAltEv.knownValue()) {
//                int kval = trueAltEv.intValue();
//                if (falseUnboxed.couldHold(kval))
//                    return falseUnboxed;
//            }
//            
//            // Otherwise apply binary numeric promotion
//            return binaryNumericPromotion(trueAltType, falseAltType);
//        }
//        
//        // Box both alternatives:
//        trueAltType = boxType(trueAltType);
//        falseAltType = boxType(falseAltType);
//        
//        if (trueAltType instanceof GenTypeSolid && falseAltType instanceof GenTypeSolid) {
//            // apply capture conversion (JLS 5.1.10) to lub() of both
//            // alternatives (JLS 15.12.2.7). I have no idea why capture conversion
//            // should be performed here, but I follow the spec blindly.
//            GenTypeSolid [] lubArgs = new GenTypeSolid[2];
//            lubArgs[0] = (GenTypeSolid) trueAltType;
//            lubArgs[1] = (GenTypeSolid) falseAltType;
//            return captureConversion(GenTypeSolid.lub(lubArgs));
//        }
//        
//        return null;
//    }
    
    /**
     * Capture conversion, as in the JLS 5.1.10
     */
    private static JavaType captureConversion(JavaType o)
    {
        GenTypeClass c = o.asClass();
        if (c != null)
            return captureConversion(c, new HashMap<String,GenTypeSolid>());
        else
            return o;
    }
    
    /**
     * Capture conversion, storing converted type parameters in the supplied Map so
     * that they are accessible to inner classes.
     *  
     * @param c   The type to perform the conversion on
     * @param tparMap   The map used for storing type parameter conversions
     * @return   The converted type.
     */
    private static GenTypeClass captureConversion(GenTypeClass c, Map<String,GenTypeSolid> tparMap)
    {
        // capture the outer type
        GenTypeClass newOuter = null;
        GenTypeClass oldOuter = c.getOuterType();
        if (oldOuter != null)
            newOuter = captureConversion(oldOuter, tparMap);
        
        // capture the arguments
        List oldArgs = c.getTypeParamList();
        List newArgs = new ArrayList(oldArgs.size());
        Iterator i = oldArgs.iterator();
        Iterator boundsIterator = c.getReflective().getTypeParams().iterator();
        while (i.hasNext()) {
            GenTypeParameter targ = (GenTypeParameter) i.next();
            GenTypeDeclTpar tpar = (GenTypeDeclTpar) boundsIterator.next();
            GenTypeSolid newArg;
            if (targ instanceof GenTypeWildcard) {
                GenTypeWildcard wc = (GenTypeWildcard) targ;
                GenTypeSolid [] ubounds = wc.getUpperBounds();
                GenTypeSolid lbound = wc.getLowerBound();
                GenTypeSolid [] tpbounds = tpar.upperBounds();
                for (int j = 0; j < tpbounds.length; j++) {
                    tpbounds[j] = (GenTypeSolid) tpbounds[j].mapTparsToTypes(tparMap);
                }
                if (lbound != null) {
                    // ? super XX
                    newArg = new GenTypeCapture(new GenTypeWildcard(tpbounds, new GenTypeSolid[] {lbound}));
                }
                else {
                    // ? extends ...
                    GenTypeSolid [] newBounds = new GenTypeSolid[ubounds.length + tpbounds.length];
                    System.arraycopy(ubounds, 0, newBounds, 0, ubounds.length);
                    System.arraycopy(tpbounds, 0, newBounds, ubounds.length, tpbounds.length);
                    newArg = new GenTypeCapture(new GenTypeWildcard(ubounds, new GenTypeSolid[0]));
                }
            }
            else {
                // The argument is not a wildcard. Capture doesn't affect it.
                newArg = (GenTypeSolid) targ;
            }
            newArgs.add(newArg);
            tparMap.put(tpar.getTparName(), newArg);
        }
        return new GenTypeClass(c.getReflective(), newArgs, newOuter);
    }
    
    /**
     * binary numeric promotion, as defined by JLS section 5.6.2. Both
     * operands must be (possibly boxed) numeric types.
     */
    public static JavaType binaryNumericPromotion(JavaType a, JavaType b)
    {
        JavaType ua = unBox(a);
        JavaType ub = unBox(b);

        if (ua.typeIs(JavaType.JT_DOUBLE) || ub.typeIs(JavaType.JT_DOUBLE))
            return JavaPrimitiveType.getDouble();

        if (ua.typeIs(JavaType.JT_FLOAT) || ub.typeIs(JavaType.JT_FLOAT))
            return JavaPrimitiveType.getFloat();

        if (ua.typeIs(JavaType.JT_LONG) || ub.typeIs(JavaType.JT_LONG))
            return JavaPrimitiveType.getLong();

        if (ua.isNumeric() && ub.isNumeric()) {
            return JavaPrimitiveType.getInt();
        }
        else {
            return null;
        }
    }
    
    /**
     * Unary numeric promotion, as defined by JLS section 5.6.1
     *  (http://java.sun.com/docs/books/jls/third_edition/html/conversions.html#5.6.1)
     * 
     */
    public static JavaType unaryNumericPromotion(JavaType a)
    {
        JavaType ua = unBox(a);
        
        // long float and double are merely unboxed; everything else is unboxed and widened to int:
        if (ua.typeIs(JavaType.JT_DOUBLE))
            return JavaPrimitiveType.getDouble();

        if (ua.typeIs(JavaType.JT_FLOAT))
            return JavaPrimitiveType.getFloat();

        if (ua.typeIs(JavaType.JT_LONG))
            return JavaPrimitiveType.getLong();
        
        if (ua.isNumeric()) {
            return JavaPrimitiveType.getInt();
        }
        else {
            return null;
        }
    }
    
    /**
     * Get the GenType of a character literal node.
     * 
     * @throws RecognitionException
     */
//    private ExprValue getCharLiteral(AST node) throws RecognitionException
//    {
//        // char literal is either 'x', or '\\uXXXX' notation, or '\t' etc.
//        String x = node.getText();
//        x = x.substring(1, x.length() - 1); // strip single quotes
//        
//        final JavaType charType = JavaPrimitiveType.getChar();
//        if (! x.startsWith("\\")) {
//            // This is the normal case
//            if (x.length() != 1)
//                throw new RecognitionException();
//            else
//                return new NumValue(charType, new Integer(x.charAt(0)));
//        }
//        else if (x.equals("\\b"))
//            return new NumValue(charType, new Integer('\b'));
//        else if (x.equals("\\t"))
//            return new NumValue(charType, new Integer('\t'));
//        else if (x.equals("\\n"))
//            return new NumValue(charType, new Integer('\n'));
//        else if (x.equals("\\f"))
//            return new NumValue(charType, new Integer('\f'));
//        else if (x.equals("\\r"))
//            return new NumValue(charType, new Integer('\r'));
//        else if (x.equals("\\\""))
//            return new NumValue(charType, new Integer('"'));
//        else if (x.equals("\\'"))
//            return new NumValue(charType, new Integer('\''));
//        else if (x.equals("\\\\"))
//            return new NumValue(charType, new Integer('\\'));
//        else if (x.startsWith("\\u")) {
//            // unicode escape, as a 4-digit hexadecimal
//            if (x.length() != 6)
//                throw new RecognitionException();
//            
//            char val = 0;
//            for (int i = 0; i < 4; i++) {
//                char digit = x.charAt(i + 2);
//                int digVal = Character.digit(digit, 16);
//                if (digVal == -1)
//                    throw new RecognitionException();
//                val = (char)(val * 16 + digVal);
//            }
//            return new NumValue(charType, new Integer(val));
//        }
//        else {
//            // octal escape, up to three digits
//            int xlen = x.length();
//            if (xlen < 2 || xlen > 4)
//                throw new RecognitionException();
//            
//            char val = 0;
//            for (int i = 0; i < xlen - 1; i++) {
//                char digit = x.charAt(i+1);
//                int digVal = Character.digit(digit, 8);
//                if (digVal == -1) {
//                        throw new RecognitionException();
//                }
//                val = (char)(val * 8 + digVal);
//            }
//            return new NumValue(charType, new Integer(val));
//        }
//    }
    
    /**
     * Get the GenType corresponding to an integer literal node.
     * @throws RecognitionException
     */
//    private ExprValue getIntLiteral(AST node, boolean negative) throws RecognitionException
//    {
//        String x = node.getText();
//        if (negative)
//            x = "-" + x;
//        
//        try {
//            Integer val = Integer.decode(x);
//            return new NumValue(JavaPrimitiveType.getInt(), val);
//        }
//        catch (NumberFormatException nfe) {
//            throw new RecognitionException();
//        }
//    }
    
    /**
     * Ge the GenType corresponding to a long literal node.
     * @throws RecognitionException
     */
//    private ExprValue getLongLiteral(AST node, boolean negative) throws RecognitionException
//    {
//        String x = node.getText();
//        if (negative)
//            x = "-" + x;
//        
//        try {
//            Long val = Long.decode(x);
//            return new NumValue(JavaPrimitiveType.getLong(), val);
//        }
//        catch (NumberFormatException nfe) {
//            throw new RecognitionException();
//        }
//    }
    
    /**
     * Get the GenType corresponding to a float literal.
     * @throws RecognitionException
     */
//    private ExprValue getFloatLiteral(AST node, boolean negative) throws RecognitionException
//    {
//        String x = node.getText();
//        if (negative)
//            x = "-" + x;
//        
//        try {
//            Float val = Float.valueOf(x);
//            return new NumValue(JavaPrimitiveType.getFloat(), val);
//        }
//        catch (NumberFormatException nfe) {
//            throw new RecognitionException();
//        }
//    }
    
    /**
     * Get the GenType corresponding to a double literal.
     * @throws RecognitionException
     */
//    private ExprValue getDoubleLiteral(AST node, boolean negative) throws RecognitionException
//    {
//        String x = node.getText();
//        if (negative)
//            x = "-" + x;
//        
//        try {
//            Double val = Double.valueOf(x);
//            return new NumValue(JavaPrimitiveType.getDouble(), val);
//        }
//        catch (NumberFormatException nfe) {
//            throw new RecognitionException();
//        }
//    }
    
    /**
     * Attempt to load, by its unqualified name,  a class which might be in the
     * current package or which might be in java.lang.
     * 
     * @param className   the name of the class to try to load
     * @param tryWildcardImports    indicates whether the class name can be resolved by
     *                              checking wildcard imports (including java.lang.*)
     * @throws ClassNotFoundException  if the class cannot be resolved/loaded
     */
//    private Class loadUnqualifiedClass(String className, boolean tryWildcardImports)
//        throws ClassNotFoundException
//    {
//        // Try singly imported types first
//        ClassEntity imported = imports.getTypeImport(className);
//        if (imported != null) {
//            try {
//                String cname = ((GenTypeClass) imported.getType()).rawName();
//                return classLoader.loadClass(cname);
//            }
//            catch (ClassNotFoundException cnfe) { }
//        }
//        
//        // It's an unqualified name - try package scope
//        try {
//            if (packageScope.length() != 0)
//                return classLoader.loadClass(packageScope + "." + className);
//            else
//                return classLoader.loadClass(className);
//        }
//        catch(ClassNotFoundException cnfe) {}
//        
//        // If not trying wildcard imports, bail out now
//        if (! tryWildcardImports)
//            throw new ClassNotFoundException(className);
//        
//        // Try wildcard imports
//        imported = imports.getTypeImportWC(className);
//        if (imported != null) {
//            try {
//                String cname = ((GenTypeClass) imported.getType()).rawName();
//                return classLoader.loadClass(cname);
//            }
//            catch (ClassNotFoundException cnfe) { }
//        }
//        
//        // Try java.lang
//        return classLoader.loadClass("java.lang." + className);
//    }
    
    
    /**
     * Get the type from a node which must by context be an inner class type.
     * @param node   The node representing the type
     * @param outer  The containing type
     * *
     * @throws SemanticException
     * @throws RecognitionException
     */
//    JavaType getInnerType(AST node, GenTypeClass outer) throws SemanticException, RecognitionException
//    {
//        if (node.getType() == JavaTokenTypes.IDENT) {
//            // A simple name<params> expression
//            List<JavaType> params = getTypeArgs(node.getFirstChild());
//            
//            String name = outer.rawName() + '$' + node.getText();
//            try {
//                Class<?> theClass = classLoader.loadClass(name);
//                Reflective r = new JavaReflective(theClass);
//                return new GenTypeClass(r, params, outer);
//            }
//            catch (ClassNotFoundException cnfe) {
//                throw new SemanticException();
//            }
//        }
//        else if (node.getType() == JavaTokenTypes.DOT) {
//            // A name.name<params> style expression
//            // The children nodes are: the qualified class name, and then
//            // the type arguments
//            AST packageNode = node.getFirstChild();
//            String dotnames = combineDotNames(packageNode, '$');
//
//            AST classNode = packageNode.getNextSibling();
//            List<JavaType> params = getTypeArgs(classNode.getFirstChild());
//
//            String name = outer.rawName() + '$' + dotnames + '$' + node.getText();
//            try {
//                Class<?> c = classLoader.loadClass(name);
//                Reflective r = new JavaReflective(c);
//                return new GenTypeClass(r, params);
//            }
//            catch(ClassNotFoundException cnfe) {
//                throw new SemanticException();
//            }
//            
//        }
//        else
//            throw new RecognitionException();
//    }
    
    /**
     * Parse a node as an entity (which could be a package, class or value).
     * @throws SemanticException
     * @throws RecognitionException
     */
//    JavaEntity getEntity(AST node) throws SemanticException, RecognitionException
//    {
//        // simple case first:
//        if (node.getType() == JavaTokenTypes.IDENT) {
//            
//            // Treat it first as a variable...
//            String nodeText = node.getText();
//            NamedValue nv = objectBench.getNamedValue(nodeText);
//            if (nv != null)
//                return new ValueEntity(nv.getGenType());
//            
//            // It's not a codepad or object bench variable, perhaps it's an import
//            List l = imports.getStaticImports(nodeText);
//            if (l != null) {
//                Iterator i = l.iterator();
//                while (i.hasNext()) {
//                    ClassEntity importEntity = (ClassEntity) i.next();
//                    try {
//                        JavaEntity fieldEnt = importEntity.getStaticField(nodeText);
//                        return fieldEnt;
//                    }
//                    catch (SemanticException se) { }
//                }
//            }
//            
//            // It might be a type
//            try {
//                Class c = loadUnqualifiedClass(nodeText, false);
//                return new TypeEntity(c);
//            }
//            catch (ClassNotFoundException cnfe) { }
//            
//            // Wildcard static imports of fields override wildcard
//            // imports of types
//            l = imports.getStaticWildcardImports();
//            Iterator i = l.iterator();
//            while (i.hasNext()) {
//                ClassEntity importEntity = (ClassEntity) i.next();
//                try {
//                    JavaEntity fieldEnt = importEntity.getStaticField(nodeText);
//                    return fieldEnt;
//                }
//                catch (SemanticException se) { }
//            }
//            
//            // Finally try wildcard type imports
//            try {
//                Class c = loadWildcardImportedType(nodeText);
//                return new TypeEntity(c);
//            }
//            catch (ClassNotFoundException cnfe) {
//                return new PackageEntity(nodeText);
//            }
//        }
//        
//        // A dot-node in the form xxx.identifier:
//        if (node.getType() == JavaTokenTypes.DOT) {
//            AST firstChild = node.getFirstChild();
//            AST secondChild = firstChild.getNextSibling();
//            if (secondChild.getType() == JavaTokenTypes.IDENT) {
//                JavaEntity firstpart = getEntity(firstChild);
//                return firstpart.getSubentity(secondChild.getText());
//            }
//            // Don't worry about xxx.super, it shouldn't be used at this
//            // level.
//        }
//        
//        // Anything else must be an expression, therefore a value:
//        JavaType exprType = getExpressionType(node).getType();
//        return new ValueEntity(exprType);
//    }
    
    /**
     * Get an entity which by context must be either a package or a (possibly
     * generic) type.
     */
//    private PackageOrClass getPackageOrType(AST node)
//        throws SemanticException, RecognitionException
//    {
//        return getPackageOrType(node, false);
//    }
    
    /**
     * Get an entity which by context must be either a package or a (possibly
     * generic) type.
     * @param node  The AST node representing the package/type
     * @param fullyQualified   True if the type must be fully qualified
     *            (if false, imports and the current package are checked for
     *            definitions of a class with the initial name)
     * 
     * @throws SemanticException
     */
//    private PackageOrClass getPackageOrType(AST node, boolean fullyQualified)
//        throws SemanticException, RecognitionException
//    {
//        // simple case first:
//        if (node.getType() == JavaTokenTypes.IDENT) {
//            // Treat it first as a type, then as a package.
//            String nodeText = node.getText();
//            List tparams = getTypeArgs(node.getFirstChild());
//            
//            try {
//                Class c;
//                if (fullyQualified) {
//                    c = classLoader.loadClass(nodeText);
//                }
//                else {
//                    c = loadUnqualifiedClass(nodeText, true);
//                }
//                TypeEntity r = new TypeEntity(c, tparams);
//                return r;
//            }
//            catch (ClassNotFoundException cnfe) {
//                // Could not be loaded as a class, so it must be a package.
//                if (! tparams.isEmpty())
//                    throw new SemanticException();
//                return new PackageEntity(nodeText);
//            }
//        }
//        
//        // A dot-node in the form xxx.identifier:
//        if (node.getType() == JavaTokenTypes.DOT) {
//            AST firstChild = node.getFirstChild();
//            AST secondChild = firstChild.getNextSibling();
//            if (secondChild.getType() == JavaTokenTypes.IDENT) {
//                List tparams = getTypeArgs(secondChild.getFirstChild());
//                PackageOrClass firstpart = getPackageOrType(firstChild, fullyQualified);
//
//                PackageOrClass entity = firstpart.getPackageOrClassMember(secondChild.getText());
//                if (! tparams.isEmpty()) {
//                    // There are type parmaters, so we must have a type
//                    if (entity.isClass()) {
//                        entity = ((ClassEntity) entity).setTypeParams(tparams);
//                    }
//                    else
//                        throw new SemanticException();
//                }
//                
//                return entity;
//            }
//        }
//        
//        throw new SemanticException();
//    }
    
    /**
     * Get an expression list node as an array of GenType
     * 
     * @throws SemanticException
     * @throws RecognitionException
     */
//    private JavaType [] getExpressionList(AST node) throws SemanticException, RecognitionException
//    {
//        int num = node.getNumberOfChildren();
//        JavaType [] r = new JavaType[num];
//        AST child = node.getFirstChild();
//        
//        // loop through the child nodes
//        for (int i = 0; i < num; i++) {
//            r[i] = getExpressionType(child).getType();
//            child = child.getNextSibling();
//        }
//        return r;
//    }
    
    /**
     * Check whether a particular method is callable with particular
     * parameters. If so return information about how specific the call is.
     * If the parameters cannot be applied to this method, return null.
     * 
     * @param targetType   The type of object/class to which the method is
     *                     being applied
     * @param tpars     The explicitly specified type parameters used in the
     *                  invocation of a generic method (list of GenTypeClass)
     * @param m       The method to check
     * @param args    The types of the arguments supplied to the method
     * @return   A record with information about the method call
     * @throws RecognitionException
     */
    private static MethodCallDesc isMethodApplicable(GenTypeClass targetType, List<GenTypeClass> tpars, MethodReflective m, JavaType [] args)
    {
        boolean methodIsVarargs = m.isVarArgs();
        MethodCallDesc rdesc = null;
        
        // First try without varargs expansion. If that fails, try with expansion.
        rdesc = isMethodApplicable(targetType, tpars, m, args, false);
        if (rdesc == null && methodIsVarargs) {
            rdesc = isMethodApplicable(targetType, tpars, m, args, true);
        }
        return rdesc;
    }

    /**
     * Check whether a particular method is callable with particular
     * parameters. If so return information about how specific the call is.
     * If the parameters cannot be applied to this method, return null.<p>
     * 
     * Normally this is called by the other variant of this method, which
     * does not take the varargs parameter.
     * 
     * @param targetType   The type of object/class to which the method is
     *                     being applied
     * @param tpars     The explicitly specified type parameters used in the
     *                  invocation of a generic method (list of GenTypeClass)
     * @param m       The method to check
     * @param args    The types of the arguments supplied to the method
     * @param varargs Whether to expand vararg parameters
     * @return   A record with information about the method call
     * @throws RecognitionException
     */
    private static MethodCallDesc isMethodApplicable(GenTypeClass targetType,
            List<GenTypeClass> tpars, MethodReflective m, JavaType [] args, boolean varargs)
    {
        boolean rawTarget = targetType.isRaw();
        boolean boxingRequired = false;
        
        // Check that the number of parameters supplied is allowable. Expand varargs
        // arguments if necessary.
        List<JavaType> mparams = m.getParamTypes();
        if (varargs) {
            // first basic check. The number of supplied arguments must be at least one less than
            // the number of formal parameters.
            if (mparams.size() > args.length + 1)
                return null;

            GenTypeArray lastArgType = mparams.get(mparams.size() - 1).getArray();
            JavaType vaType = lastArgType.getArrayComponent();
            List<JavaType> expandedParams = new ArrayList<JavaType>(args.length);
            expandedParams.addAll(mparams);
            for (int i = mparams.size(); i < args.length; i++) {
                expandedParams.set(i, vaType);
            }
            mparams = expandedParams;
        }
        else {
            // Not varargs: supplied arguments must match formal parameters
            if (mparams.size() != args.length)
                return null;
        }
        
        // Get type parameters of the method
        List<GenTypeDeclTpar> tparams = Collections.emptyList();
        if ((! rawTarget) || m.isStatic())
            tparams = m.getTparTypes();
        
        // Number of type parameters supplied must match number declared, unless either
        // is zero. Section 15.12.2 of the JLS, "a non generic method may be applicable
        // to an invocation which supplies type arguments" (in which case the type args
        // are ignored).
        if (! tpars.isEmpty() && ! tparams.isEmpty() && tpars.size() != tparams.size())
            return null;
        
        // Set up a map we can use to put actual/inferred type arguments. Initialise it
        // with the target type's arguments.
        Map<String,GenTypeParameter> tparMap;
        if (rawTarget)
            tparMap = new HashMap<String,GenTypeParameter>();
        else
            tparMap = targetType.getMap();

        // Perform type inference, if necessary
        if (! tparams.isEmpty() && tpars.isEmpty()) {
            // Our initial map has the class type parameters, minus those which are
            // shadowed by the method's type parameters (map to themselves).
            for (Iterator<GenTypeDeclTpar> i = tparams.iterator(); i.hasNext(); ) {
                GenTypeDeclTpar tpar = (GenTypeDeclTpar) i.next();
                tparMap.put(tpar.getTparName(), tpar);
            }
            
            Map<String,Set<GenTypeSolid>> tlbConstraints = new HashMap<String,Set<GenTypeSolid>>();
            Map teqConstraints = new HashMap();
            
            // Time for some type inference
            for (int i = 0; i < mparams.size(); i++) {
                if (mparams.get(i).isPrimitive())
                    continue;
                
                GenTypeSolid mparam = (GenTypeSolid) mparams.get(i);
                mparam = mparam.mapTparsToTypes(tparMap);
                processAtoFConstraint(args[i], mparam, tlbConstraints, teqConstraints);
            }
            
            // what we have now is a map with tpar constraints.
            // Some tpars may not have been constrained: these are inferred to be the
            // intersection of their upper bounds.
            tpars = new ArrayList();
            Iterator i = tparams.iterator();
            while (i.hasNext()) {
                GenTypeDeclTpar fTpar = (GenTypeDeclTpar) i.next();
                String tparName = fTpar.getTparName();
                GenTypeSolid eqConstraint = (GenTypeSolid) teqConstraints.get(tparName);
                // If there's no equality constraint, use the lower bound constraints
                if (eqConstraint == null) {
                    Set lbConstraintSet = (Set) tlbConstraints.get(tparName);
                    if (lbConstraintSet != null) {
                        GenTypeSolid [] lbounds = (GenTypeSolid []) lbConstraintSet.toArray(new GenTypeSolid[lbConstraintSet.size()]);
                        eqConstraint = GenTypeSolid.lub(lbounds); 
                    }
                    else {
                        // no equality or lower bound constraints: use the upper
                        // bounds of the tpar
                        eqConstraint = fTpar.getBound();
                    }
                }
                eqConstraint = (GenTypeSolid) eqConstraint.mapTparsToTypes(tparMap);
                tpars.add((GenTypeClass) eqConstraint);
                tparMap.put(tparName, eqConstraint);
            }
        }
        else {
            // Get a map of type parameter names to types from the target type
            // complete the type parameter map with tpars of the method
            Iterator<GenTypeDeclTpar> formalI = tparams.iterator();
            Iterator<GenTypeClass> actualI = tpars.iterator();
            while (formalI.hasNext()) {
                GenTypeDeclTpar formalTpar = (GenTypeDeclTpar) formalI.next();
                GenTypeSolid argTpar = (GenTypeSolid) actualI.next();
                
                // first we check that the argument type is a subtype of the
                // declared type.
                GenTypeSolid [] formalUbounds = formalTpar.upperBounds();
                for (int i = 0; i < formalUbounds.length; i++) {
                    formalUbounds[i] = (GenTypeSolid) formalUbounds[i].mapTparsToTypes(tparMap);
                    if (formalUbounds[i].isAssignableFrom(argTpar))
                        break;
                    if (i == formalUbounds.length - 1)
                        return null;
                }
                
                tparMap.put(formalTpar.getTparName(), argTpar);
            }
        }
        
        // For each argument, must check the compatibility of the supplied
        // parameter type with the argument type; and if neither the formal
        // parameter or supplied argument are raw, then must check generic
        // contract as well.
        
        for (int i = 0; i < args.length; i++) {
            JavaType formalArg = mparams.get(i);
            JavaType givenParam = args[i];
            
            // Substitute type arguments.
            formalArg = formalArg.mapTparsToTypes(tparMap);
            
            // check if the given parameter doesn't match the formal argument
            if (! formalArg.isAssignableFrom(givenParam)) {
                // a boxing conversion followed by a widening reference conversion
                if (! formalArg.isAssignableFrom(boxType(givenParam))) {
                    // an unboxing conversion followed by a widening primitive conversion
                    if (! formalArg.isAssignableFrom(unBox(givenParam))) {
                        return null;
                    }
                }
                boxingRequired = true;
            }
        }
        
        JavaType rType = m.getReturnType().mapTparsToTypes(tparMap);
        return new MethodCallDesc(m, mparams, varargs, boxingRequired, rType);
    }

    
    
    /**
     * Process a type inference constraint of the form "A is convertible to F".
     * Note F must be a valid formal parameter: it can't be a wildcard with multiple
     * bounds or an intersection type.
     * 
     * @param a  The argument type
     * @param f  The formal parameter type
     * @param tlbConstraints   lower bound constraints (a Map to Set of GenTypeSolid)
     * @param teqConstraints   equality constraints (a Map to GenTypeSolid)
     */
    private static void processAtoFConstraint(JavaType a, GenTypeSolid f, Map tlbConstraints, Map teqConstraints)
    {
        a = boxType(a);
        if (a.isPrimitive())
            return; // no constraint
        
        if (f instanceof GenTypeTpar) {
            // The constraint T :> A is implied
            GenTypeTpar t = (GenTypeTpar) f;
            Set constraintsSet = (Set) tlbConstraints.get(t.getTparName());
            if (constraintsSet == null) {
                constraintsSet = new HashSet();
                tlbConstraints.put(t.getTparName(), constraintsSet);
            }
            
            constraintsSet.add(a);
        }
        
        // If F is an array of the form U[], and a is an array of the form V[]...
        else if (f.getArrayComponent() != null) {
            if (a.getArrayComponent() != null) {
                if (f.getArrayComponent() instanceof GenTypeSolid) {
                    a = a.getArrayComponent();
                    f = (GenTypeSolid) f.getArrayComponent();
                    processAtoFConstraint(a, f, tlbConstraints, teqConstraints);
                }
            }
        }
        
        // If F is of the form G<...> and A is convertible to the same form...
        else {
            GenTypeClass cf = (GenTypeClass) f;
            Map fMap = cf.getMap();
            if (fMap != null && a instanceof GenTypeSolid) {
                GenTypeClass [] asts = ((GenTypeSolid) a).getReferenceSupertypes();
                for (int i = 0; i < asts.length; i++) {
                    try {
                        GenTypeClass aMapped = asts[i].mapToSuper(cf.classloaderName());
                        // Superclass relationship is by capture conversion
                        if (! asts[i].classloaderName().equals(cf.classloaderName()))
                            aMapped = (GenTypeClass) captureConversion(aMapped);
                        Map aMap = aMapped.getMap();
                        if (aMap != null) {
                            Iterator j = fMap.keySet().iterator();
                            while (j.hasNext()) {
                                String tpName = (String) j.next();
                                GenTypeParameter fPar = (GenTypeParameter) fMap.get(tpName);
                                GenTypeParameter aPar = (GenTypeParameter) aMap.get(tpName);
                                processAtoFtpar(aPar, fPar, tlbConstraints, teqConstraints);
                            }
                        }
                    }
                    catch (BadInheritanceChainException bice) {}
                }
            }
        }
        return;
    }
    
    /**
     * Process type parameters from a type inference constraint A convertible-to F.
     */
    private static void processAtoFtpar(GenTypeParameter aPar, GenTypeParameter fPar, Map tlbConstraints, Map teqConstraints)
    {
        if (fPar instanceof GenTypeSolid) {
            if (aPar instanceof GenTypeSolid) {
                // aPar = fPar
                processAeqFConstraint((GenTypeSolid) aPar, (GenTypeSolid) fPar, tlbConstraints, teqConstraints);
            }
        } else {
            GenTypeSolid flbound = fPar.getLowerBound();
            if (flbound != null) {
                // F-par is of form "? super ..."
                GenTypeSolid albound = aPar.getLowerBound();
                if (albound != null) {
                    // there should only be one element in albounds
                    // recurse with albounds[0] >> flbound[0]
                    processFtoAConstraint(albound, flbound, tlbConstraints, teqConstraints);
                }
            } else {
                // F-par is of form "? extends ..."
                GenTypeSolid [] fubounds = fPar.getUpperBounds();
                GenTypeSolid [] aubounds = aPar.getUpperBounds();
                if (fubounds.length > 0 && aubounds.length > 0) {
                    // recurse with aubounds << fubounds[0]
                    processAtoFConstraint(IntersectionType.getIntersection(aubounds), fubounds[0], tlbConstraints, teqConstraints);
                }
            }
        }
    }
    
    /**
     * Process a type inference constraint of the form "A is equal to F".
     */
    private static void processAeqFConstraint(GenTypeSolid a, GenTypeSolid f, Map tlbConstraints, Map teqConstraints)
    {
        if (f instanceof GenTypeTpar) {
            // The constraint T == A is implied.
            GenTypeTpar t = (GenTypeTpar) f;
            teqConstraints.put(t.getTparName(), a);
        }
        
        else if (f.getArrayComponent() instanceof GenTypeSolid) {
            // "If F = U[] ... if A is an array type V[], or a type variable with an
            // upper bound that is an array type V[]..."
            GenTypeSolid [] asts;
            if (a instanceof GenTypeDeclTpar)
                asts = ((GenTypeDeclTpar) a).upperBounds();
            else
                asts = new GenTypeSolid[] {a};
            
            for (int i = 0; i < asts.length; i++) {
                JavaType act = asts[i].getArrayComponent();
                if (act instanceof GenTypeSolid) {
                    processAeqFConstraint((GenTypeSolid) act, (GenTypeSolid) f.getArrayComponent(), tlbConstraints, teqConstraints);
                }
            }
        }
        
        else {
            GenTypeClass cf = f.asClass();
            GenTypeClass af = a.asClass();
            if (af != null && cf != null) {
                if (cf.classloaderName().equals(af.classloaderName())) {
                    Map fMap = cf.getMap();
                    Map aMap = af.getMap();
                    if (fMap != null && aMap != null) {
                        Iterator j = fMap.keySet().iterator();
                        while (j.hasNext()) {
                            String tpName = (String) j.next();
                            GenTypeParameter fPar = (GenTypeParameter) fMap.get(tpName);
                            GenTypeParameter aPar = (GenTypeParameter) aMap.get(tpName);
                            processAeqFtpar(aPar, fPar, tlbConstraints, teqConstraints);
                        }
                    }
                }
            }
        }
    }

    /**
     * Process type parameters from a type inference constraint A equal-to F.
     */
    private static void processAeqFtpar(GenTypeParameter aPar, GenTypeParameter fPar, Map tlbConstraints, Map teqConstraints)
    {
        if (aPar instanceof GenTypeSolid && fPar instanceof GenTypeSolid) {
            processAeqFConstraint((GenTypeSolid) aPar, (GenTypeSolid) fPar, tlbConstraints, teqConstraints);
        }
        else if (aPar instanceof GenTypeWildcard && fPar instanceof GenTypeWildcard) {
            GenTypeSolid flBound = fPar.getLowerBound();
            GenTypeSolid [] fuBounds = fPar.getUpperBounds();
            // F = ? super U,  A = ? super V
            if (flBound != null) {
                GenTypeSolid alBound = aPar.getLowerBound();
                if (alBound != null)
                    processAeqFConstraint(alBound, flBound, tlbConstraints, teqConstraints);
            }
            // F = ? extends U, A = ? extends V
            else if (fuBounds.length != 0) {
                GenTypeSolid [] auBounds = aPar.getUpperBounds();
                if (auBounds.length != 0)
                    processAeqFConstraint(IntersectionType.getIntersection(auBounds), fuBounds[0], tlbConstraints, teqConstraints);
            }
        }
    }

    /**
     * Process a type inference constraint of the form "F is convertible to A".
     */
    private static void processFtoAConstraint(GenTypeSolid a, GenTypeSolid f, Map tlbConstraints, Map teqConstraints)
    {
        // This is pretty much nothing like what the JLS says it should be. As far as I can
        // make out, the JLS is just plain wrong.
        
        // If F = T, then T <: A is implied: but we cannot make use of such a constraint.
        // If F = U[] ...
        if (f.getArrayComponent() instanceof GenTypeSolid) {
            // "If F = U[] ... if A is an array type V[], or a type variable with an
            // upper bound that is an array type V[]..."
            GenTypeSolid [] asts;
            if (a instanceof GenTypeDeclTpar)
                asts = ((GenTypeDeclTpar) a).upperBounds();
            else
                asts = new GenTypeSolid[] {a};
            
            for (int i = 0; i < asts.length; i++) {
                JavaType act = asts[i].getArrayComponent();
                if (act instanceof GenTypeSolid) {
                    processFtoAConstraint((GenTypeSolid) act, (GenTypeSolid) f.getArrayComponent(), tlbConstraints, teqConstraints);
                }
            }
        }
        
        else if (f.asClass() != null) {
            GenTypeClass cf = f.asClass();
            if (! (a instanceof GenTypeTpar)) {
                GenTypeClass [] asts = a.getReferenceSupertypes();
                for (int i = 0; i < asts.length; i++) {
                    try {
                        GenTypeClass fMapped = cf.mapToSuper(asts[i].classloaderName());
                        Map<String,GenTypeParameter> aMap = asts[i].getMap();
                        Map<String,GenTypeParameter> fMap = fMapped.getMap();
                        if (aMap != null && fMap != null) {
                            Iterator<String> j = fMap.keySet().iterator();
                            while (j.hasNext()) {
                                String tpName = j.next();
                                GenTypeParameter fPar = fMap.get(tpName);
                                GenTypeParameter aPar = aMap.get(tpName);
                                processFtoAtpar(aPar, fPar, tlbConstraints, teqConstraints);
                            }
                        }
                    }
                    catch (BadInheritanceChainException bice) {}
                }
            }
        }
    }

    /**
     * Process type parameters from a type inference constraint F convertible-to A.
     */
    private static void processFtoAtpar(GenTypeParameter aPar, GenTypeParameter fPar, Map tlbConstraints, Map teqConstraints)
    {
        if (fPar instanceof GenTypeSolid) {
            if (aPar instanceof GenTypeSolid) {
                processAeqFConstraint((GenTypeSolid) aPar, (GenTypeSolid) fPar, tlbConstraints, teqConstraints);
            }
            else {
                GenTypeSolid alBound = aPar.getLowerBound();
                if (alBound != null) {
                    // aPar is of the form "? super ..."
                    processAtoFConstraint(alBound, (GenTypeSolid) fPar, tlbConstraints, teqConstraints);
                }
                else {
                    GenTypeSolid [] auBounds = aPar.getUpperBounds();
                    if (auBounds.length != 0) {
                        processFtoAConstraint(auBounds[0], (GenTypeSolid) fPar, tlbConstraints, teqConstraints);
                    }
                }
            }
        }
        
        else {
            // fPar must be a wildcard
            GenTypeSolid flBound = fPar.getLowerBound();
            if (flBound != null) {
                if (aPar instanceof GenTypeWildcard) {
                    // fPar is ? super ...
                    GenTypeSolid alBound = aPar.getLowerBound();
                    if (alBound != null) {
                        processAtoFConstraint(alBound, flBound, tlbConstraints, teqConstraints);
                    }
                }
                else {
                    // fPar is ? extends ...
                    GenTypeSolid [] fuBounds = fPar.getUpperBounds();
                    GenTypeSolid [] auBounds = aPar.getUpperBounds();
                    if (auBounds.length != 0 && fuBounds.length != 0) {
                        processFtoAConstraint(auBounds[0], fuBounds[0], tlbConstraints, teqConstraints);
                    }
                }
            }
        }
    }

    /**
     * Get the return type of a method call expression. This is quite
     * complicated; see JLS section 15.12
     *
     * @throws RecognitionException
     * @throws SemanticException
     */
//    private JavaType getMethodCallReturnType(AST node) throws RecognitionException, SemanticException
//    {
//        // For a method call node, the first child is the method name
//        // (possibly a dot-name) and the second is an ELIST.
//        //
//        // In the case of the method name being a dot-name, it may also
//        // be a generic type. In this case the children of the dot-node
//        // are:
//        //
//        // <object-expression> <type-arg-1> <type-arg-2> .... <methodname>
//        //
//        // Where <object-expression> may actually be a type (ie. invoking
//        // a static method).
//        
//        AST firstArg = node.getFirstChild();
//        AST secondArg = firstArg.getNextSibling();
//        if (secondArg.getType() != JavaTokenTypes.ELIST)
//            throw new RecognitionException();
//        
//        // we don't handle a variety of cases such
//        // as "this.xxx()" or "super.xxxx()".
//        
//        if (firstArg.getType() == JavaTokenTypes.IDENT) {
//            // It's an unqualified method call. In the context of a code pad
//            // statement, it can only be an imported static method call.
//            
//            String mname = firstArg.getText();
//            JavaType [] argumentTypes = getExpressionList(secondArg);
//            
//            List<ClassEntity> l = imports.getStaticImports(mname);
//            MethodCallDesc candidate = findImportedMethod(l, mname, argumentTypes);
//            
//            if (candidate == null) {
//                // There were no non-wildcard static imports. Try wildcard imports.
//                l = imports.getStaticWildcardImports();
//                candidate = findImportedMethod(l, mname, argumentTypes);
//            }
//            
//            if (candidate != null)
//                return captureConversion(candidate.retType);
//            
//            // no suitable candidates
//            throw new SemanticException();
//        }
//        
//        if (firstArg.getType() == JavaTokenTypes.DOT) {
//            AST targetNode = firstArg.getFirstChild();
//            JavaEntity callTarget = getEntity(targetNode);
//            JavaType targetType = callTarget.getType();
//                            
//            // now get method name, and argument types;
//            List typeArgs = new ArrayList(5);
//            JavaType [] argumentTypes = getExpressionList(secondArg);
//            String methodName = null;
//            AST searchNode = targetNode.getNextSibling();
//            
//            // get the type arguments and method name
//            while (searchNode != null) {
//                int nodeType = searchNode.getType();
//                // type argument?
//                if (nodeType == JavaTokenTypes.TYPE_ARGUMENT) {
//                    JavaType taType = getType(searchNode.getFirstChild());
//                    typeArgs.add(taType);
//                }
//                // method name?
//                else if (nodeType == JavaTokenTypes.IDENT) {
//                    methodName = searchNode.getText();
//                    break;
//                }
//                else
//                    break;
//                
//                searchNode = searchNode.getNextSibling();
//            }
//            
//            // If no method name, this doesn't seem to be valid grammar
//            if (methodName == null)
//                throw new RecognitionException();
//            
//            // getClass() is a special case, it should return Class<? extends
//            // basetype>
//            if (targetType instanceof GenTypeParameterizable) {
//                if (methodName.equals("getClass") && argumentTypes.length == 0) {
//                    List paramsl = new ArrayList(1);
//                    paramsl.add(new GenTypeExtends((GenTypeSolid) targetType.getErasedType()));
//                    return new GenTypeClass(new JavaReflective(Class.class), paramsl);
//                }
//            }
//            
//            // apply capture conversion to target
//            targetType = captureConversion(targetType);
//            
//            if (! (targetType instanceof GenTypeSolid))
//                throw new SemanticException();
//                
//            GenTypeSolid targetTypeS = (GenTypeSolid) targetType;
//            GenTypeClass [] rsts = targetTypeS.getReferenceSupertypes();
//            
//            // match the call to a method:
//            ArrayList suitableMethods = getSuitableMethods(methodName, rsts, argumentTypes, typeArgs);
//            
//            if (suitableMethods.size() != 0) {
//                MethodCallDesc mcd = (MethodCallDesc) suitableMethods.get(0);
//                // JLS 15.12.2.6, we must apply capture conversion
//                return captureConversion(mcd.retType);
//            }
//            // ambiguity
//            throw new SemanticException();
//        }
//        
//        // anything else is an unknown
//        throw new RecognitionException();
//    }
    
    /**
     * Find the most specific imported method with the given name and argument types
     * in the given list of imports.
     * 
     * @param imports         A list of imports (ClassEntity)
     * @param mname           The name of the method to find
     * @param argumentTypes   The type of each supplied argument
     * @return   A descriptor for the most specific method, or null if none found
     */
//    private MethodCallDesc findImportedMethod(List imports, String mname, JavaType [] argumentTypes)
//    {
//        MethodCallDesc candidate = null;
//        
//        // Iterate through the imports
//        Iterator i = imports.iterator();
//        while (i.hasNext()) {
//            ClassEntity importEntity = (ClassEntity) i.next();
//            List r = importEntity.getStaticMethods(mname);
//            Iterator j = r.iterator();
//            while (j.hasNext()) {
//                // For each matching method, assess its applicability. If applicable,
//                // and it is the most specific method yet found, keep it.
//                Method m = (Method) j.next();
//                MethodCallDesc mcd = isMethodApplicable(importEntity.getClassType(), Collections.EMPTY_LIST, m, argumentTypes);
//                if (mcd != null) {
//                    if (candidate == null) {
//                        candidate = mcd;
//                    }
//                    else {
//                        if (mcd.compareSpecificity(candidate) == 1)
//                            candidate = mcd;
//                    }
//                }
//            }
//        }
//        return candidate;
//    }
    
    
    /**
     * Get the candidate list of methods with the given name and argument types. The returned
     * list will be the maximally specific methods (as defined by the JLS 15.12.2.5).
     * 
     * @param methodName    The name of the method
     * @param targetTypes   The types to search for declarations of this method
     * @param argumentTypes The types of the arguments supplied in the method invocation
     * @param typeArgs      The type arguments, if any, supplied in the method invocation
     * @return  an ArrayList of MethodCallDesc - the list of candidate methods
     * @throws RecognitionException
     */
    public static ArrayList<MethodCallDesc> getSuitableMethods(String methodName,
            GenTypeClass [] targetTypes, JavaType [] argumentTypes, List<GenTypeClass> typeArgs)
    {
        ArrayList<MethodCallDesc> suitableMethods = new ArrayList<MethodCallDesc>();
        for (int k = 0; k < targetTypes.length; k++) {
            GenTypeClass targetClass = targetTypes[k];
            Map<String,Set<MethodReflective>> methodMap = targetClass.getReflective()
                    .getDeclaredMethods();
            Set<MethodReflective> methods = methodMap.get(methodName);

            if (methods == null) {
                continue;
            }
            
            // Find methods that are applicable, and
            // accessible. See JLS 15.12.2.1.
            for (MethodReflective method : methods) {

                // check that the method is applicable (and under
                // what constraints)
                MethodCallDesc mcd = isMethodApplicable(targetClass, typeArgs, method, argumentTypes);

                // Iterate through the current candidates, and:
                // - replace one or more of them with this one
                //   (this one is more precise)
                //   OR
                // - add this one (no more or less precise than
                //   any other candidates)
                //   OR
                // - discard this one (less precise than another)

                if (mcd != null) {
                    boolean replaced = false;
                    for (int j = 0; j < suitableMethods.size(); j++) {
                        //suitableMethods.add(methods[i]);
                        MethodCallDesc mc = (MethodCallDesc) suitableMethods.get(j);
                        int compare = mcd.compareSpecificity(mc);
                        if (compare == 1) {
                            // this method is more specific
                            suitableMethods.remove(j);
                            j--;
                        }
                        else if (compare == -1) {
                            // other method is more specific
                            replaced = true;
                            break;
                        }
                    }

                    if (! replaced)
                        suitableMethods.add(mcd);
                }
            }
        }
        return suitableMethods;
    }
    
    /**
     * Unbox a type, if it is a class type which represents a primitive type
     * in object form (eg. java.lang.Integer).<p>
     * 
     * Other class types are returned unchanged.<p>
     * 
     * To determine whether unboxing occurred, compare the result with the
     * object which was passed in. (The same object will be returned if no
     * unboxing took place).
     * 
     * @param b  The type to unbox
     * @return  The unboxed type
     */
    public static JavaType unBox(JavaType b)
    {
        GenTypeClass c = b.asClass();
        if (c != null) {
            String cName = c.classloaderName();
            if (cName.equals("java.lang.Integer"))
                return JavaPrimitiveType.getInt();
            else if (cName.equals("java.lang.Long"))
                return JavaPrimitiveType.getLong();
            else if (cName.equals("java.lang.Short"))
                return JavaPrimitiveType.getShort();
            else if (cName.equals("java.lang.Byte"))
                return JavaPrimitiveType.getByte();
            else if (cName.equals("java.lang.Character"))
                return JavaPrimitiveType.getChar();
            else if (cName.equals("java.lang.Float"))
                return JavaPrimitiveType.getFloat();
            else if (cName.equals("java.lang.Double"))
                return JavaPrimitiveType.getDouble();
            else if (cName.equals("java.lang.Boolean"))
                return JavaPrimitiveType.getBoolean();
            else
                return b;
        }
        else
            return b;
    }
    
    /**
     * Box a type, if it is a primitive type such as "int".<p>
     * 
     * Other types are returned unchanged.<p>
     * 
     * To determine whether boxing occurred, compare the result with the
     * object which was passed in. (The same object will be returned if no
     * boxing took place).
     * 
     * @param b  The type to box
     * @return  The boxed type
     */
    private static JavaType boxType(JavaType u)
    {
        if (u instanceof JavaPrimitiveType) {
            if (u.typeIs(JavaType.JT_INT))
                return new GenTypeClass(new JavaReflective(Integer.class));
            else if (u.typeIs(JavaType.JT_LONG))
                return new GenTypeClass(new JavaReflective(Long.class));
            else if (u.typeIs(JavaType.JT_SHORT))
                return new GenTypeClass(new JavaReflective(Short.class));
            else if (u.typeIs(JavaType.JT_BYTE))
                return new GenTypeClass(new JavaReflective(Byte.class));
            else if (u.typeIs(JavaType.JT_CHAR))
                return new GenTypeClass(new JavaReflective(Character.class));
            else if (u.typeIs(JavaType.JT_FLOAT))
                return new GenTypeClass(new JavaReflective(Float.class));
            else if (u.typeIs(JavaType.JT_DOUBLE))
                return new GenTypeClass(new JavaReflective(Double.class));
            else if (u.typeIs(JavaType.JT_BOOLEAN))
                return new GenTypeClass(new JavaReflective(Boolean.class));
            else
                return u;
        }
        else
            return u;
    }
    
    static public boolean isBoxedBoolean(JavaType t)
    {
        GenTypeClass ct = t.asClass();
        if (ct != null) {
            return ct.classloaderName().equals("java.lang.Boolean");
        }
        else
            return false;
    }
    
    /**
     * Conditionally box a type. The type is only boxed if the boolean flag
     * passed in the second parameter is true.<p>
     * 
     * This is a helper method to improve readability.<p>
     * 
     * @see TextAnalyzer#boxType(JavaType)
     * 
     * @param u    The type to box
     * @param box  The flag indicating whether boxing should occur
     * @return
     */
//    private JavaType maybeBox(JavaType u, boolean box)
//    {
//        if (box)
//            return boxType(u);
//        else
//            return u;
//    }
    
    /**
     * Check if a member of some class is accessible from the context of the given
     * package. This will be the case if the member is public, if the member is
     * protected and declared in the same class, or if the member is package-
     * private and declared in the same class.
     * 
     * @param declaringClass  The class which declares the member
     * @param mods            The member modifier flags (as returned by getModifiers())
     * @param pkg             The package to check access for
     * @return  true if the package has access to the member
     */
    static boolean isAccessible(Class declaringClass, int mods, String pkg)
    {
        if (Modifier.isPrivate(mods))
            return false;
        
        if (Modifier.isPublic(mods))
            return true;
        
        // get the package of the class
        String className = declaringClass.getName();
        int lastDot = className.lastIndexOf('.');
        if (lastDot == -1)
            lastDot = 0;
        String classPkg = className.substring(0, lastDot);
        
        // it's not private nor public - so it's package private (or protected).
        // It is therefore accessible if the accessing package is the same as
        // the declaring package.
        return classPkg.equals(pkg);
    }
    
    /**
     * Find (if one exists) an accessible field with the given name in the given class (and
     * its supertypes). The "getField(String)" method in java.lang.Class does the same thing,
     * except it doesn't take into account accessible package-private fields which is
     * important.
     * 
     * @param c    The class in which to find the field
     * @param fieldName   The name of the accessible field to find
     * @param pkg         The package context from which the field is accessed
     * @param searchSupertypes  Whether to search in supertypes for the field
     * @return      The field
     * @throws NoSuchFieldException  if no accessible field with the given name exists
     */
    static Field getAccessibleField(Class c, String fieldName, String pkg, boolean searchSupertypes)
        throws NoSuchFieldException
    {
        String className = c.getName();
        int lastDot = className.lastIndexOf('.');
        if (lastDot == -1)
            lastDot = 0;
        
        String classPkg = className.substring(0, lastDot);
        
        // package private members accessible if the package is the same
        boolean pprivateAccessible = classPkg.equals(pkg);

        try {
            // Try fields declared in this class
            Field [] cfields = c.getDeclaredFields();
            for (int i = 0; i < cfields.length; i++) {
                if (cfields[i].getName().equals(fieldName)) {
                    int mods = cfields[i].getModifiers();
                    // rule out private fields
                    if (! Modifier.isPrivate(mods)) {
                        // now, if the fields is public, or package-private fields are
                        // accessible, then the field is accessible.
                        if (pprivateAccessible || Modifier.isPublic(mods)) {
                            return cfields[i];
                        }
                    }
                }
            }
            
            if (searchSupertypes) {
                // Try fields declared in superinterfaces
                Class [] ifaces = c.getInterfaces();
                for (int i = 0; i < ifaces.length; i++) {
                    try {
                        return getAccessibleField(ifaces[i], fieldName, pkg, true);
                    }
                    catch (NoSuchFieldException nsfe) { }
                }
                
                // Try fields declared in superclass
                Class sclass = c.getSuperclass();
                if (sclass != null)
                    return getAccessibleField(sclass, fieldName, pkg, true);
            }
        }
        catch (LinkageError le) { }
        
        throw new NoSuchFieldException();
    }
    
    /**
     * Get a list of accessible static methods declared in the given class with the
     * given name. The list includes public methods and, if the class is in the designated
     * package, package-private and protected methods.
     * 
     * @param c  The class in which to find the methods
     * @param methodName  The name of the methods to find
     * @param pkg   The accessing package
     * @return  A list of java.lang.reflect.Method
     */
    static List getAccessibleStaticMethods(Class c, String methodName, String pkg)
    {
        String className = c.getName();
        int lastDot = className.lastIndexOf('.');
        if (lastDot == -1)
            lastDot = 0;
        
        String classPkg = className.substring(0, lastDot);
        
        // package private members accessible if the package is the same
        boolean pprivateAccessible = classPkg.equals(pkg);

        try {
            List rlist = new ArrayList();
            
            // Now find methods declared in this class
            Method [] cmethods = c.getDeclaredMethods();
            methodLoop:
            for (int i = 0; i < cmethods.length; i++) {
                if (cmethods[i].getName().equals(methodName)) {
                    int mods = cmethods[i].getModifiers();
                    if (Modifier.isPrivate(mods) || ! Modifier.isStatic(mods))
                        continue methodLoop;
                    
                    if (! Modifier.isPublic(mods) && ! pprivateAccessible)
                        continue methodLoop;
                    
                    if (jutils.isSynthetic(cmethods[i]))
                        continue methodLoop;
                    
                    rlist.add(cmethods[i]);
                }
            }
            return rlist;
        }
        catch (LinkageError le) { }
        
        return Collections.EMPTY_LIST;
    }
    
    /**
     * A simple structure to hold various information about a method call.
     * 
     * @author Davin McCall
     */
    public static class MethodCallDesc
    {
        public MethodReflective method;
        public List<JavaType> argTypes; // list of GenType
        public boolean vararg;   // is a vararg call
        public boolean autoboxing; // requires autoboxing
        public JavaType retType; // effective return type (before capture conversion)
        
        /**
         * Constructor for MethodCallDesc.
         * 
         * @param m   The method being called
         * @param argTypes   The effective types of the arguments, as an
         *                   ordered list
         * @param vararg      Whether the method is being called as a vararg
         *                    method
         * @param autoboxing  Whether autoboxing is required for parameters
         * @param retType     The effective return type
         */
        public MethodCallDesc(MethodReflective m, List<JavaType> argTypes, boolean vararg, boolean autoboxing, JavaType retType)
        {
            this.method = m;
            this.argTypes = argTypes;
            this.vararg = vararg;
            this.autoboxing = autoboxing;
            this.retType = retType;
        }
        
        /**
         * Find out which (if any) method call is strictly more specific than the
         * other. Both calls must be valid calls to the same method with the same
         * number of parameters.
         * 
         * @param other  The method to compare with
         * @return 1 if this method is more specific;
         *         -1 if the other method is more specific;
         *         0 if neither method is more specific than the other.
         * 
         * See JLS 15.12.2.5 (by "more specific", we mean what the JLS calls
         * "strictly more specific", more or less. We also take arity and
         * abstractness into account)
         */
        public int compareSpecificity(MethodCallDesc other)
        {
            if (other.vararg && ! vararg)
                return 1; // we are more specific
            if (! other.vararg && vararg)
                return -1; // we are less specific
            
            // I am reasonably sure this gives the same result as the algorithm
            // described in the JLS section 15.12.2.5, and it has the advantage
            // of being a great deal simpler.
            Iterator<JavaType> i = argTypes.iterator();
            Iterator<JavaType> j = other.argTypes.iterator();
            int upCount = 0;
            int downCount = 0;
            
            while (i.hasNext()) {
                JavaType myArg = i.next();
                JavaType otherArg = j.next();
                
                if (myArg.isAssignableFrom(otherArg)) {
                    if (! otherArg.isAssignableFrom(myArg))
                        upCount++;
                }
                else if (otherArg.isAssignableFrom(myArg))
                    downCount++;
            }
            
            if (upCount > 0 && downCount == 0)
                return -1; // other is more specific
            else if (downCount > 0 && upCount == 0)
                return 1;  // other is less specific
            
            // finally, if one method is abstract and the other is not,
            // then the non-abstract method is more specific.
            boolean isAbstract = method.isAbstract();
            boolean otherAbstract = other.method.isAbstract();
            if (isAbstract && ! otherAbstract)
                return -1;
            else if (! isAbstract && otherAbstract)
                return 1;
            else
                return 0;
        }
    }
    
    /**
     * A value (possibly unknown) with assosciated type
     */
    static class ExprValue
    {
        // default implementation has no known value
        public JavaType type;
        
        public ExprValue(JavaType type)
        {
            this.type = type;
        }
        
        public JavaType getType()
        {
            return type;
        }
        
        public boolean knownValue()
        {
            return false;
        }
        
        public int intValue()
        {
            throw new UnsupportedOperationException();
        }
        
        public long longValue()
        {
            throw new UnsupportedOperationException();
        }
        
        public float floatValue()
        {
            throw new UnsupportedOperationException();
        }
        
        public double doubleValue()
        {
            throw new UnsupportedOperationException();
        }
        
        public boolean booleanValue()
        {
            throw new UnsupportedOperationException();
        }
    }
    
    static class BooleanValue extends ExprValue
    {
        boolean val;

        // constructor is private: use getBooleanValue instead
        private BooleanValue(boolean val)
        {
            super(JavaPrimitiveType.getBoolean());
            this.val = val;
        }
        
        // cache the two values
        public static BooleanValue trueVal = null;
        public static BooleanValue falseVal = null;
        
        /**
         * Get an instance of BooleanValue, representing either true or false.
         */
        public static BooleanValue getBooleanValue(boolean val)
        {
            if (val == true) {
                if (trueVal == null)
                    trueVal = new BooleanValue(true);
                return trueVal;
            }
            else {
                if (falseVal == null)
                    falseVal = new BooleanValue(false);
                return falseVal;
            }
        }
        
        public boolean booleanValue()
        {
            return val;
        }
    }
    
    class NumValue extends ExprValue
    {
        private Number val;
        
        NumValue(JavaType type, Number val)
        {
            super(type);
            this.val = val;
        }
        
        public boolean knownValue()
        {
            return true;
        }
        
        public int intValue()
        {
            return val.intValue();
        }
        
        public long longValue()
        {
            return val.longValue();
        }
        
        public float floatValue()
        {
            return val.floatValue();
        }
        
        public double doubleValue()
        {
            return val.doubleValue();
        }
    }
}
