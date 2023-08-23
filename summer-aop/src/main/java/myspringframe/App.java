package myspringframe;

import myspringframe.Utils.t2;
import myspringframe.annotation.ComponentScan;
import myspringframe.aop.ProxyResolver;
import myspringframe.context.AnnotationConfigApplicationContext;
import myspringframe.io.propertyresolver.PropertyResolver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Hello world!
 *
 */
@ComponentScan
public class App 
{
    public static void main( String[] args ) throws IOException, URISyntaxException
    {
        AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext(ProxyResolver.class,new PropertyResolver(new Properties()));
        t2 test = app.getBean(t2.class);
        test.show();
    }
}
