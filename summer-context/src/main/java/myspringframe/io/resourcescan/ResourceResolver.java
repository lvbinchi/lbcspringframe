package myspringframe.io.resourcescan;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;


public class ResourceResolver {
    String basePackage;

    public ResourceResolver(String backPackage){
        this.basePackage=backPackage;
    }
    public <R> List<R> scan(Function<Resource,R> mapper) throws IOException, URISyntaxException {
        System.out.println(basePackage);
        String basePackagePath = this.basePackage.replace(".", "/");
        String path = basePackagePath;
        List<R> res=new ArrayList<>();
        ClassLoader classLoader=getContextClassLoader();
        Enumeration<URL> en = classLoader.getResources(path);
        while (en.hasMoreElements()){
            URI uri=en.nextElement().toURI();
            String uriStr = removeTrailingSlash(uri.toString());
            String uriBaseStr = uriStr.substring(0, uriStr.length() - basePackagePath.length());
            if (uriStr.startsWith("file:")){
                uriBaseStr=uriBaseStr.substring(5);
            }
            if (uriStr.startsWith("jar:")){
                scanFile(true,uriBaseStr,jarUriTopath(basePackagePath,uri),res,mapper);
            }
            else {
                scanFile(false,uriBaseStr, Paths.get(uri),res,mapper);
            }
        }
        return res;
    }

    public <R> void scanFile(boolean isJar, String base, Path root,List<R> collector,Function<Resource,R> mapper) throws
            IOException{
        String baseDir=removeTrailingSlash(base);
        Files.walk(root).filter(Files::isRegularFile).forEach(file->{
            Resource res=null;
            if (isJar){
                res=new Resource(baseDir,removeLeadingSlash(file.toString()));
            }
            else{
                String path=file.toString();
                String name=removeLeadingSlash(path.substring(baseDir.length()));
                res=new Resource(path, name);
            }
            R r=mapper.apply(res);
            if (r!=null) collector.add(r);
        });
    }
    public ClassLoader getContextClassLoader(){
        ClassLoader cl=null;
        cl=Thread.currentThread().getContextClassLoader();
        if (cl==null) cl=getClass().getClassLoader();
        return cl;
    }
    String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    String removeLeadingSlash(String s) {
        if (s.startsWith("/") || s.startsWith("\\")) {
            s = s.substring(1);
        }
        return s;
    }

    Path jarUriTopath(String basePackagePath, URI jarUri) throws IOException{
        Map<String, String> env = new HashMap<>();
        FileSystem fs = FileSystems.newFileSystem(jarUri, env);
        Path path = fs.getPath(basePackagePath);
        return path;
    }


}
