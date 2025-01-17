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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.text.BadLocationException;

import junit.framework.TestCase;
import bluej.debugger.gentype.GenTypeClass;
import bluej.debugger.gentype.MethodReflective;
import bluej.editor.moe.MoeSyntaxDocument;
import bluej.parser.entity.ClassLoaderResolver;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.PackageOrClass;
import bluej.parser.entity.PackageResolver;
import bluej.parser.entity.TypeEntity;
import bluej.parser.nodes.ParsedCUNode;
import bluej.parser.nodes.ParsedNode;
import bluej.parser.nodes.NodeTree.NodeAndPosition;

public class EditorParserTest extends TestCase
{
    {
        InitConfig.init();
    }
    
    private TestEntityResolver resolver;
    
    @Override
    protected void setUp() throws Exception
    {
        resolver = new TestEntityResolver(new ClassLoaderResolver(this.getClass().getClassLoader()));
    }
    
    @Override
    protected void tearDown() throws Exception
    {
    }
    
    /**
     * Generate a compilation unit node based on some source code.
     */
    private ParsedCUNode cuForSource(String sourceCode, String pkg)
    {
        EntityResolver resolver = new PackageResolver(this.resolver, pkg);
        MoeSyntaxDocument document = new MoeSyntaxDocument(resolver);
        try {
            document.insertString(0, sourceCode, null);
        }
        catch (BadLocationException ble) {}
        return document.getParser();
    }

    public void test1()
    {
        String sourceCode = ""
            + "class A\n"       // position 0
            + "{\n"             // position 8 
            + "   class B\n"    // position 10 
            + "    {\n"         // position 21 
            + "    }\n"
            + "}\n";
            
        ParsedCUNode pcuNode = cuForSource(sourceCode, "");
        NodeAndPosition classNP = pcuNode.findNodeAtOrAfter(0, 0);
        assertEquals(ParsedNode.NODETYPE_TYPEDEF, classNP.getNode().getNodeType());
        assertEquals(0, classNP.getPosition());
        
        NodeAndPosition innerNP = classNP.getNode().findNodeAtOrAfter(9, 0);
        
        NodeAndPosition classBNP = innerNP.getNode().findNodeAtOrAfter(innerNP.getPosition(),
                innerNP.getPosition());
        assertEquals(ParsedNode.NODETYPE_TYPEDEF, classBNP.getNode().getNodeType());
        assertEquals(13, classBNP.getPosition());
    }
    
    /**
     * Test that a method defined inside a class is recognized properly.
     */
    public void test2()
    {
        String aClassSrc = "class A {\n" +
        "  public String someMethod() {\n" +
        "    return \"hello\";\n" +
        "  }\n" +
        "}\n";

        ParsedCUNode aNode = cuForSource(aClassSrc, "");
        resolver.addCompilationUnit("", aNode);
        
        EntityResolver resolver = new PackageResolver(this.resolver, "");
        TypeEntity aClassEnt = resolver.resolvePackageOrClass("A", null).resolveAsType();
        GenTypeClass aClass = aClassEnt.getType().getCapture().asClass();
        Map<String,Set<MethodReflective>> methods = aClass.getReflective().getDeclaredMethods();
        Set<MethodReflective> mset = methods.get("someMethod");
        assertEquals(1, mset.size());
        
        MethodReflective method = mset.iterator().next();
        assertEquals("java.lang.String", method.getReturnType().toString(false));
    }

    /**
     * Test that a broken method call doesn't interfere with containing method position/size
     */
    public void test3()
    {
        String aClassSrc = "class A {\n" +   // position 0
        "  public void someMethod() {\n" +   // position 10
        "    methodCall(\n" +                // position 39 
        "  }\n" +                            // position 55 
        "}\n";

        ParsedCUNode aNode = cuForSource(aClassSrc, "");
        NodeAndPosition classNP = aNode.findNodeAtOrAfter(0, 0);
        assertEquals(ParsedNode.NODETYPE_TYPEDEF, classNP.getNode().getNodeType());
        assertEquals(0, classNP.getPosition());
        
        NodeAndPosition innerNP = classNP.getNode().findNodeAtOrAfter(9, 0);
        
        NodeAndPosition methodNP = innerNP.getNode().findNodeAtOrAfter(
                innerNP.getPosition(), innerNP.getPosition());
        
        assertEquals(12, methodNP.getPosition());
        assertEquals(58, methodNP.getPosition() + methodNP.getSize());
    }
    
    /**
     * Test parsing a broken source doesn't break the parser...
     */
    public void testBroken()
    {
        String sourceCode = ""
            + "class A\n"       // position 0
            + "{\n"             // position 8
            + "  A() {\n"
            + "    int\n"
            + "  }"
            + "}\n";
            
        ParsedCUNode pcuNode = cuForSource(sourceCode, "");
        assertNotNull(pcuNode);
    }
    
    public void testSuperclass()
    {
        String sourceCode = ""
            + "class A { }\n";
        ParsedCUNode aNode = cuForSource(sourceCode, "");
        PackageOrClass apoc = aNode.resolvePackageOrClass("A", null);
        assertNotNull(apoc);
        TypeEntity aTyent = apoc.resolveAsType();
        assertNotNull(aTyent);
        GenTypeClass aClass = aTyent.getType().asClass();
        assertNotNull(aClass);
        List<GenTypeClass> supers = aClass.getReflective().getSuperTypes();
        assertEquals(1, supers.size());
        assertEquals("java.lang.Object", supers.get(0).toString());
        resolver.addCompilationUnit("", aNode);
        
        sourceCode = "class B extends A {}\n";
        ParsedCUNode bNode = cuForSource(sourceCode, "");
        PackageOrClass bpoc = bNode.resolvePackageOrClass("B", null);
        assertNotNull(bpoc);
        TypeEntity bTyent = bpoc.resolveAsType();
        assertNotNull(bTyent);
        GenTypeClass bClass = bTyent.getType().asClass();
        assertNotNull(bClass);
        supers = bClass.getReflective().getSuperTypes();
        assertEquals(1, supers.size());
        assertEquals("A", supers.get(0).toString());
    }

    public void testImport()
    {
        String abcSrc = "package xyz; public class abc { public static class def { }}";
        ParsedCUNode abcNode = cuForSource(abcSrc, "xyz");
        resolver.addCompilationUnit("xyz", abcNode);
        
        String defSrc = "package abc; public class def { }";
        ParsedCUNode defNode = cuForSource(defSrc, "abc");
        resolver.addCompilationUnit("abc", defNode);
        
        String tSrc = "package xyz; import abc.def; class T { public static def field; }";
        ParsedCUNode tNode = cuForSource(tSrc, "xyz");
        resolver.addCompilationUnit("xyz", tNode);
        
        // We want to check that "import abc.def" correctly imports the "def" class from
        // the "abc" package rather than importing the "def" inner class from the "abc"
        // class in the current package ("xyz").
        TypeEntity tent = resolver.resolveQualifiedClass("xyz.T");
        assertNotNull(tent);
        JavaEntity fEnt = tent.getSubentity("field", null);
        assertNotNull(fEnt);
        JavaEntity fVal = fEnt.resolveAsValue();
        assertNotNull(fVal);
        assertEquals("abc.def", fVal.getType().toString());
    }
}
