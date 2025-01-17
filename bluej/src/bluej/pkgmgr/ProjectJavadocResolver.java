/*
 This file is part of the BlueJ program. 
 Copyright (C) 2010  Michael Kolling and John Rosenberg 
 
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
package bluej.pkgmgr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.MethodReflective;
import bluej.debugger.gentype.Reflective;
import bluej.parser.InfoParser;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.PackageResolver;
import bluej.parser.symtab.ClassInfo;
import bluej.utility.JavaNames;
import bluej.views.Comment;
import bluej.views.MethodView;
import bluej.views.View;

/**
 * Resolves javadoc from classes within a project.
 * 
 * @author Davin McCall
 */
public class ProjectJavadocResolver implements JavadocResolver
{
    private Project project;
    private CommentCache commentCache = new CommentCache();
    
    public ProjectJavadocResolver(Project project)
    {
        this.project = project;
    }
    
    public void getJavadoc(MethodReflective method)
    {
        Reflective declaring = method.getDeclaringType();
        String declName = declaring.getName();
        String methodSig = buildSig(method);
        
        try {
            Class<?> cl = project.getClassLoader().loadClass(declName);
            View clView = View.getView(cl);
            MethodView [] methods = clView.getAllMethods();
            
            for (int i = 0; i < methods.length; i++) {
                if (methodSig.equals(methods[i].getSignature())) {
                    Comment comment = methods[i].getComment();
                    if (comment != null) {
                        method.setJavaDoc(comment.getText());
                        List<String> paramNames = new ArrayList<String>(comment.getParamCount());
                        for (int j = 0; j < comment.getParamCount(); j++) {
                            paramNames.add(comment.getParamName(j));
                        }
                        method.setParamNames(paramNames);
                        return;
                    }
                    break;
                }
            }
        }
        catch (ClassNotFoundException cnfe) {}
        catch (LinkageError e) {}
        
        Properties comments = commentCache.get(declName);
        if (comments == null) {
            comments = getCommentsFromSource(declName);
            if (comments == null) {
                return;
            }
            commentCache.put(declName, comments);
        }

        // Find the comment for the particular method we want
        for (int i = 0; ; i++) {
            String comtarget = comments.getProperty("comment" + i + ".target");
            if (comtarget == null) {
                break;
            }
            if (comtarget.equals(methodSig)) {
                method.setJavaDoc(comments.getProperty("comment" + i + ".text"));
                String paramNames = comments.getProperty("comment" + i + ".params");
                StringTokenizer tokenizer = new StringTokenizer(paramNames);
                List<String> paramNamesList = new ArrayList<String>();
                while (tokenizer.hasMoreTokens()) {
                    paramNamesList.add(tokenizer.nextToken());
                }
                method.setParamNames(paramNamesList);
                break;
            }
        }
    }

    /**
     * Find the javadoc for a given class (target) by searching the project source path.
     * In particular, this normally includes the JDK source. When source for the required
     * class is found, it is parsed to extract comments.
     */
    private Properties getCommentsFromSource(String target)
    {
        List<File> sourcePath = project.getSourcePath();
        String pkg = JavaNames.getPrefix(target);
        String entName = target.replace('.', '/') + ".java";
        
        for (File pathEntry : sourcePath) {
            if (pathEntry.isFile()) {
                try {
                    ZipFile zipFile = new ZipFile(pathEntry);
                    ZipEntry zipEnt = zipFile.getEntry(entName);
                    if (zipEnt != null) {
                        InputStream zeis = zipFile.getInputStream(zipEnt);
                        Reader r = new InputStreamReader(zeis);
                        EntityResolver resolver = new PackageResolver(project.getEntityResolver(),
                                pkg);
                        ClassInfo info = InfoParser.parse(r, resolver, null);
                        if (info == null) {
                            return null;
                        }
                        return info.getComments();
                    }
                }
                catch (IOException ioe) {}
                catch (Exception e) {}  // probably a parse exception
            }
        }
        
        return null;
    }
    
    /**
     * Build a method signature from a MethodReflective.
     */
    private static String buildSig(MethodReflective method)
    {
        String sig = method.getReturnType().getErasedType().toString();
        sig = sig.replace('$', '.');
        sig += ' ';
        
        sig += method.getName() + '(';
        Iterator<JavaType> i = method.getParamTypes().iterator();
        while (i.hasNext()) {
            JavaType ptype = i.next();
            sig += ptype.getErasedType().toString().replace('$', '.');
            if (i.hasNext()) {
                sig += ", ";
            }
        }
        sig += ')';
        
        return sig;
    }
}
