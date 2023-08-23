package myspringframe.webmvc;

import myspringframe.context.AnnotationConfigApplicationContext;
import myspringframe.context.ApplicationContext;
import myspringframe.exception.NestedRuntimeException;
import myspringframe.io.propertyresolver.PropertyResolver;
import myspringframe.utils.WebUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.io.IOException;
import java.net.URISyntaxException;

public class ContextLoaderListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext servletContext = sce.getServletContext();
        WebMvcConfiguration.setServletContext(servletContext);
        PropertyResolver propertyResolver = WebUtils.createPropertyResolver();
        String encoding = propertyResolver.getProperty("${summer.web.character-encoding:UTF-8}");
        servletContext.setRequestCharacterEncoding(encoding);
        servletContext.setResponseCharacterEncoding(encoding);
        try {
            ApplicationContext applicationContext = createApplicationContext(servletContext.getInitParameter("configuration"),propertyResolver);
            WebUtils.registerDispatcherServlet(servletContext,propertyResolver);
            servletContext.setAttribute("applicationContext",applicationContext);
        }
        catch (Exception e){
            throw new NestedRuntimeException("Cannot init ApplicationContext for missing init param name: configuration");
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        Object object = sce.getServletContext().getAttribute("applicationContext");
        if (object instanceof ApplicationContext) {
            ApplicationContext applicationContext = (ApplicationContext) object;
            applicationContext.close();
        }
    }

    public AnnotationConfigApplicationContext createApplicationContext(String configClassName,PropertyResolver propertyResolver) throws
            IOException, URISyntaxException
    {
        if (configClassName == null || propertyResolver == null) {
            throw new NestedRuntimeException("Cannot init ApplicationContext for missing init param name: configuration");
        }
        Class<?> configClass;
        try{
            configClass = Class.forName(configClassName);
        }
        catch (ClassNotFoundException e){
            throw new NestedRuntimeException("Could not load class from init param 'configuration': " + configClassName);
        }
        return new AnnotationConfigApplicationContext(configClass,propertyResolver);

    }
}
