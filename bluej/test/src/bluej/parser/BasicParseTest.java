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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.swing.text.BadLocationException;

import bluej.editor.moe.MoeSyntaxDocument;
import bluej.parser.entity.ClassLoaderResolver;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.PackageResolver;
import bluej.parser.nodes.ParsedCUNode;
import bluej.parser.symtab.ClassInfo;
import bluej.parser.symtab.Selection;

/**
 * Run a whole directory of sample source files through our parser.
 *
 * @author  Andrew Patterson
 */
public class BasicParseTest extends junit.framework.TestCase
{
    /**
     * Get a data or result file from our hidden stash..
     * NOTE: the stash of data files is in the ast/data directory.
     */
    private File getFile(String name)
    {
        URL url = getClass().getResource("/bluej/parser/ast/data/" + name);
        
        if (url == null || url.getFile().equals(""))
            return null;
        else
            return new File(url.getFile());
    }
    
    /**
     * Sets up the test fixture.
     *
     * Called before every test case method.
     */
    protected void setUp()
    {
    }

    /**
     * Tears down the test fixture.
     *
     * Called after every test case method.
     */
    protected void tearDown()
    {
    }

    /**
     * Find a target method/class in the comments and return its index (or -1 if not found).
     */
    private int findTarget(Properties comments, String target)
    {
        for (int commentNum = 0; ; commentNum++) {
            String comment = comments.getProperty("comment" + commentNum + ".target");
            if (comment == null) {
                return -1;
            }
            if (comment.equals(target)) {
                return commentNum;
            }
        }
    }
    
    /**
     * Lots of sample files, none of which should cause exceptions
     * in our parser.
     * 
     * @throws Exception
     */
    public void testNoParseExceptionsOnStandardCode()
        throws Exception
    {
        // this file came from some guys web page.. it just includes lots of
        // Java constructs
        ClassParser.parse(getFile("java_basic.dat"));

        // these files came from the test suite accompanying antlr
        ClassParser.parse(getFile("A.dat"));
        ClassParser.parse(getFile("B.dat"));
        ClassParser.parse(getFile("C.dat"));
        ClassParser.parse(getFile("D.dat"));
        ClassParser.parse(getFile("E.dat"));
        
        // these files were added later
        ClassParser.parse(getFile("F.dat"));
        ClassParser.parse(getFile("G.dat"));
        
    }
    
    public void testNoParseExceptionsOnGenerics()
    	throws Exception
    {
    	// Parse generics
    	ClassParser.parse(getFile("15_generic.dat"));
    }
    
    public void testCode()
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );

        StringReader sr = new StringReader(
                "class A {\n" +
                "  Class<int[]> cc = int[].class;" +
                "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, ter, "testpkg");
        assertNotNull(info);
    }
    
    public void testValidClassInfo()
        throws Exception
    {
        List<String> references = new ArrayList<String>();
        references.add("Insets");
        references.add("Color");
        references.add("Rectangle");
        references.add("Graphics");
        references.add("Graphics2D");
        references.add("Runnable");
        references.add("Exception");
        references.add("Dummy1");
        references.add("Dummy2");
        
        File file = getFile("AffinedTransformer.dat");
        ClassInfo info = ClassParser.parse(file, new ClassLoaderResolver(this.getClass().getClassLoader()));

        assertEquals("AffinedTransformer",info.getName());
        assertEquals("javax.swing.JFrame",info.getSuperclass());
        assertEquals("bluej.parser.ast.data",info.getPackage());

        //assertEquals(7, info.getUsed().size());
        
        // Check package selections
        Selection testSel = info.getPackageNameSelection();
        assertEquals(1, testSel.getLine());
        assertEquals(9, testSel.getColumn());
        assertEquals(1, testSel.getEndLine());
        assertEquals(30, testSel.getEndColumn());
        
        testSel = info.getPackageSemiSelection();
        assertEquals(1, testSel.getLine());
        assertEquals(30, testSel.getColumn());
        assertEquals(1, testSel.getEndLine());
        assertEquals(31, testSel.getEndColumn());
        
        testSel = info.getPackageStatementSelection();
        assertEquals(1, testSel.getLine());
        assertEquals(1, testSel.getColumn());
        assertEquals(1, testSel.getEndLine());
        assertEquals(8, testSel.getEndColumn());
        
        // AffinedTransformer already extends JFrame
        Selection extendsInsert = info.getExtendsInsertSelection();
        assertNull(extendsInsert);
        
        // No type parameters
        List<String> l = info.getTypeParameterTexts();
        if (l != null)
            assertEquals(0, l.size());
//        testSel = info.getTypeParametersSelection();
//        assertNull(testSel);
        
        // Implements insert
        Selection implementsInsert = info.getImplementsInsertSelection();
        assertEquals(47, implementsInsert.getEndColumn());
        assertEquals(47, implementsInsert.getColumn());
        assertEquals(6, implementsInsert.getEndLine());
        assertEquals(6, implementsInsert.getLine());

        Selection superReplace = info.getSuperReplaceSelection();
        assertEquals(6, superReplace.getLine());
        assertEquals(41, superReplace.getColumn());
        assertEquals(6, superReplace.getEndLine());
        assertEquals(47, superReplace.getEndColumn());
        
        // Check that comment is created with parameter names
        Properties comments = info.getComments();
        
        String wantedComment = "void resizeToInternalSize(int, int)";
        int wci = findTarget(comments, wantedComment);
        assertTrue(wci != -1);
        String paramNames = comments.getProperty("comment" + wci + ".params");
        assertEquals("internalWidth internalHeight", paramNames);
        
        /*
         * Second file - no superclass, multiple interfaces 
         */
        
        file = getFile("multi_interface.dat");
        info = ClassParser.parse(file);
        
        extendsInsert = info.getExtendsInsertSelection();
        assertEquals(10, extendsInsert.getEndColumn());
        assertEquals(10, extendsInsert.getColumn());
        assertEquals(1, extendsInsert.getEndLine());
        assertEquals(1, extendsInsert.getLine());
        
        // the implements insert selection should be just beyond the
        // end of the last implemented interface
        implementsInsert = info.getImplementsInsertSelection();
        assertEquals(32, implementsInsert.getEndColumn());
        assertEquals(32, implementsInsert.getColumn());
        assertEquals(1, implementsInsert.getEndLine());
        assertEquals(1, implementsInsert.getLine());
        
        // the interface selections: "implements" "AA" "," "BB" "," "CC"
        List<Selection> interfaceSels = info.getInterfaceSelections();
        assertEquals(6, interfaceSels.size());
        Iterator<Selection> i = interfaceSels.iterator();
        
        // "implements"
        Selection interfaceSel = (Selection) i.next();
        assertEquals(1, interfaceSel.getLine());
        assertEquals(11, interfaceSel.getColumn());
        assertEquals(1, interfaceSel.getEndLine());
        assertEquals(21, interfaceSel.getEndColumn());
        
        // "AA"
        interfaceSel = (Selection) i.next();
        assertEquals(1, interfaceSel.getLine());
        assertEquals(22, interfaceSel.getColumn());
        assertEquals(1, interfaceSel.getEndLine());
        assertEquals(24, interfaceSel.getEndColumn());

        // ", "
        interfaceSel = (Selection) i.next();
        assertEquals(1, interfaceSel.getLine());
        assertEquals(24, interfaceSel.getColumn());
        assertEquals(1, interfaceSel.getEndLine());
        assertEquals(26, interfaceSel.getEndColumn());

        // "BB"
        interfaceSel = (Selection) i.next();
        assertEquals(1, interfaceSel.getLine());
        assertEquals(26, interfaceSel.getColumn());
        assertEquals(1, interfaceSel.getEndLine());
        assertEquals(28, interfaceSel.getEndColumn());

        // ", "
        interfaceSel = (Selection) i.next();
        assertEquals(1, interfaceSel.getLine());
        assertEquals(28, interfaceSel.getColumn());
        assertEquals(1, interfaceSel.getEndLine());
        assertEquals(30, interfaceSel.getEndColumn());

        // "CC"
        interfaceSel = (Selection) i.next();
        assertEquals(1, interfaceSel.getLine());
        assertEquals(30, interfaceSel.getColumn());
        assertEquals(1, interfaceSel.getEndLine());
        assertEquals(32, interfaceSel.getEndColumn());
    }
    
    public void testValidClassInfo2() throws Exception
    {
    	StringReader sr = new StringReader(
    			"class A implements Runnable, Iterable {\n" +
    			"  void someMethod() {\n" +
    			"    I i = new I();\n" +
    			"  }\n" +
    			"}\n"
    	);
    	ClassInfo info = ClassParser.parse(sr,
    	        new ClassLoaderResolver(this.getClass().getClassLoader()), null);
    	List<String> implemented = info.getImplements();
    	assertNotNull(implemented);
    	assertEquals(2, implemented.size());
    	assertTrue(implemented.contains("java.lang.Runnable"));
    	assertTrue(implemented.contains("java.lang.Iterable"));
    }

    /**
     * Test recognition of interfaces
     */
    public void testValidClassInfo3() throws Exception
    {
    	StringReader sr = new StringReader(
    			"interface A {}"
    	);
    	ClassInfo info = ClassParser.parse(sr, null, null);
    	assertTrue(info.isInterface());
    }

    /**
     * Test recognition of enumerations
     */
    public void testValidClassInfo4() throws Exception
    {
    	StringReader sr = new StringReader(
    			"enum A { monday, tuesday, wednesday }"
    	);
    	ClassInfo info = ClassParser.parse(sr, null, null);
    	assertTrue(info.isEnum());
    }
    
    public void testMultiDimensionalArrayParam() throws Exception
    {
        File file = getFile("I.dat");
        ClassInfo info = ClassParser.parse(file);
        
        // Check that comment is created with parameter names
        Properties comments = info.getComments();
        
        String wantedComment = "void method(int[][])";
        int commentNum = findTarget(comments, wantedComment);
        assertTrue(commentNum != -1);
        String paramNames = comments.getProperty("comment" + commentNum + ".params");
        assertEquals(paramNames, "args");
    }
    
    public void testCommentExtraction() throws Exception
    {
        String aSrc = "class A {\n"
            + "  void method1(int [] a) { }\n"
            + "  void method2(int a[]) { }\n"
            + "}\n";
        
        ClassInfo info = ClassParser.parse(new StringReader(aSrc), null, null);
        Properties comments = info.getComments();
        assertTrue(findTarget(comments, "void method1(int[])") != -1);
        assertTrue(findTarget(comments, "void method2(int[])") != -1);
    }
    
    private ParsedCUNode cuForSource(String sourceCode, EntityResolver resolver)
    {
        MoeSyntaxDocument document = new MoeSyntaxDocument(resolver);
        try {
            document.insertString(0, sourceCode, null);
        }
        catch (BadLocationException ble) {}
        return document.getParser();
    }
    
    public void testDependencyAnalysis()
        throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "");
        ter.addCompilationUnit("", cuForSource("class I {}", pkgr));
        ter.addCompilationUnit("", cuForSource("class J<T> {}", pkgr));
        ter.addCompilationUnit("", cuForSource("class K {}", pkgr));
        ter.addCompilationUnit("", cuForSource("class L {}", pkgr));
        ter.addCompilationUnit("", cuForSource("class M {}", pkgr));
        
        FileInputStream fis = new FileInputStream(getFile("H.dat"));
        ClassInfo info = ClassParser.parse(new InputStreamReader(fis), pkgr, "");
        
        List<String> used = info.getUsed();
        assertTrue(used.contains("I")); 
        assertTrue(used.contains("J")); 
        assertTrue(used.contains("K")); 
        assertTrue(used.contains("L")); 
        assertTrue(used.contains("M")); 
    }
    
    /**
     * Test dependency analysis works correctly in the presence of inner classes.
     * In this example, the "I" in the method body refers to the inner class "I" and
     * should not generate an external reference.
     */
    public void testDependencyAnalysis2() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        ter.addCompilationUnit("", cuForSource("class I {}", ter));
    	StringReader sr = new StringReader(
    			"class A {\n" +
    			"  void someMethod() {\n" +
    			"    I i = new I();\n" +
    			"  }\n" +
    			"  class I { }\n" +
    			"}\n"
    	);
    	ClassInfo info = ClassParser.parse(sr, null, null);
    	List<String> used = info.getUsed();
    	
    	assertFalse(used.contains("I"));
    }

    /**
     * Test loop iterator variable declaration dependency
     */
    public void testDependencyAnalysis3() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "");
        ter.addCompilationUnit("", cuForSource("class I {}", pkgr));
        ter.addCompilationUnit("", cuForSource("class JJ { public static I someMethod() { return null; } }", pkgr));
        
        StringReader sr = new StringReader(
                        "class A {\n" +
                        "  void someMethod() {\n" +
                        "    for(I ii = JJ.someMethod(); ;) ;\n" +
                        "  }\n" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "");
        List<String> used = info.getUsed();
        
        assertTrue(used.contains("I"));
    }
    
    /**
     * Test reference to class via static method call
     */
    public void testDependencyAnalysis4() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "");
        ter.addCompilationUnit("", cuForSource("class I {}", pkgr));
        ter.addCompilationUnit("", cuForSource("class JJ { public static I someMethod() { return null; } }", pkgr));
        
        StringReader sr = new StringReader(
                        "class A {\n" +
                        "  void someMethod() {\n" +
                        "    for(I ii = JJ.someMethod(); ;) ;\n" +
                        "  }\n" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "");
        List<String> used = info.getUsed();
        
        assertTrue(used.contains("JJ"));
    }
    
    /**
     * Test that type parameters are recognized and that they shadow classes with the same name
     */
    public void testDependencyAnalysis5() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        ter.addCompilationUnit("", cuForSource("class T {}", ter));
        
        StringReader sr = new StringReader(
                        "class A<T> {\n" +
                        "  public T someVar;" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, ter, "");
        List<String> used = info.getUsed();
        
        assertFalse(used.contains("T"));
    }

    /**
     * Test dependency analysis within a named package
     */
    public void testDependencyAnalysis6() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "testpkg");
        ter.addCompilationUnit("testpkg", cuForSource("package testpkg; class N {}", pkgr));
        
        StringReader sr = new StringReader(
                        "package testpkg;" +
                        "class A {\n" +
                        "  public N someVar;" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "testpkg");
        List<String> used = info.getUsed();
        
        assertTrue(used.contains("N"));
    }

    /**
     * Test dependency analysis handles qualified names
     */
    public void testDependencyAnalysis7() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "testpkg");
        PackageResolver pkgmr = new PackageResolver(ter, "otherpkg");
        ter.addCompilationUnit("testpkg", cuForSource("package testpkg; class N {}", pkgr));
        ter.addCompilationUnit("otherpkg", cuForSource("package otherpkg; class M {}", pkgmr));
        
        StringReader sr = new StringReader(
                        "package testpkg;" +
                        "class A {\n" +
                        "  public testpkg.N someVar;" +
                        "  public otherpkg.M otherVar;" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "testpkg");
        List<String> used = info.getUsed();
        
        assertTrue(used.contains("N"));
        assertFalse(used.contains("M"));
    }
    
    /**
     * Test that an imported class shadows another class in the same package.
     */
    public void testDependencyAnalysis8() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        ter.addCompilationUnit("testpkg", cuForSource("class N {}", ter));
        ter.addCompilationUnit("otherpkg", cuForSource("class N {}", ter));
        
        StringReader sr = new StringReader(
                        "package testpkg;" +
                        "import otherpkg.N;" +
                        "class A {\n" +
                        "  public N someVar;" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, ter, "testpkg");
        List<String> used = info.getUsed();
        
        assertFalse(used.contains("N"));
    }

    /**
     * Test reference to class via static value reference
     */
    public void testDependencyAnalysis9() throws Exception
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "");
        ter.addCompilationUnit("", cuForSource("class I { public static int xyz = 3; }", pkgr));
        
        StringReader sr = new StringReader(
                        "class A {\n" +
                        "  int n = I.xyz;" +
                        "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "");
        List<String> used = info.getUsed();
        
        assertTrue(used.contains("I"));
    }
    
    /**
     * Test that a type argument generates a dependency.
     */
    public void testDependencyAnalysis10()
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "");
        ter.addCompilationUnit("", cuForSource("class I { }", pkgr));

        StringReader sr = new StringReader(
                "import java.util.List;" +
                "class A {\n" +
                "  List<I> list;" +
                "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "");
        List<String> used = info.getUsed();

        assertTrue(used.contains("I"));
    }

    /**
     * Test that a type parameter bound generates a dependency.
     */
    public void testDependencyAnalysis11()
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "");
        ter.addCompilationUnit("", cuForSource("class I { }", pkgr));

        StringReader sr = new StringReader(
                "class A<T extends I> {\n" +
                "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "");
        List<String> used = info.getUsed();

        assertTrue(used.contains("I"));
    }
    
    public void testDependencyAnalysis12()
    {
        InitConfig.init();
        TestEntityResolver ter = new TestEntityResolver(
                new ClassLoaderResolver(this.getClass().getClassLoader())
                );
        PackageResolver pkgr = new PackageResolver(ter, "testpkg");
        ter.addCompilationUnit("testpkg", cuForSource("package testpkg; class I { }", pkgr));
        ter.addCompilationUnit("testpkg", cuForSource("package testpkg; class J { }", pkgr));

        StringReader sr = new StringReader(
                "package testpkg;" +
                "class A {\n" +
                "  Class<?> cc = I.class;" +
                "  Class<?> cc2 = testpkg.J.class;" +
                "}\n"
        );
        ClassInfo info = ClassParser.parse(sr, pkgr, "testpkg");
        List<String> used = info.getUsed();

        assertTrue(used.contains("I"));
        assertTrue(used.contains("J"));
    }
}
