package myspringframe.aop;

import myspringframe.context.ApplicationContextUtils;
import myspringframe.context.BeanDefinition;
import myspringframe.context.BeanPostProcessor;
import myspringframe.context.ConfigurableApplicationContext;
import myspringframe.exception.AopConfigException;
import myspringframe.exception.BeansException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public abstract class AnnotationProxyBeanPostProcessor<A extends Annotation> implements BeanPostProcessor {
    Map<String, Object> originBeans = new HashMap<>();
    Class<A> annotationClass;

    public AnnotationProxyBeanPostProcessor() {
        this.annotationClass = getParameterizedType();
    }

    public Class<A> getParameterizedType(){
        Type type = getClass().getGenericSuperclass();
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException("Class " + getClass().getName() + " does not have parameterized type.");
        }
        ParameterizedType pt = (ParameterizedType) type;
        Type[] types = pt.getActualTypeArguments();
        if (types.length != 1){
            throw new IllegalArgumentException("Class " + getClass().getName() + " has more than 1 parameterized type.");
        }
        Type r = types[0];
        if (!(r instanceof Class<?>)) {
            throw new IllegalArgumentException("Class " + getClass().getName() + " does not have parameterized type of class.");
        }
        return (Class<A>) r;



    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();
        A annotaion = beanClass.getAnnotation(annotationClass);
        if (annotaion != null){
            String handlerName;
            try{
                handlerName = (String) annotaion.annotationType().getMethod("value").invoke(annotaion);
            }catch (ReflectiveOperationException e) {
                throw new AopConfigException(String.format("@%s must have value() returned String type.", this.annotationClass.getSimpleName()), e);
            }
            Object proxy = createProxy(beanClass,bean,handlerName);
            originBeans.put(beanName, bean);
            return proxy;
        }
        return bean;
    }

    Object createProxy(Class<?> clazz,Object bean,String handlerName){
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextUtils.getRequiredApplicationContext();
        BeanDefinition def = ctx.findBeanDefinition(handlerName);
        if (def == null) {
            throw new AopConfigException(String.format("@%s proxy handler '%s' not found.", this.annotationClass.getSimpleName(), handlerName));
        }
        Object handlerBean = def.getInstance();
        if (handlerBean == null) {
            handlerBean = ctx.createBeanAsEarlySingleton(def);
        }
        ProxyResolver proxyResolver = ProxyResolver.getInstance();
        if (handlerBean instanceof InvocationHandler) {
            InvocationHandler handler = (InvocationHandler) handlerBean;
            return ProxyResolver.getInstance().createProxy(bean, handler);
        }else {
            throw new AopConfigException(String.format("@%s proxy handler '%s' is not type of %s.", this.annotationClass.getSimpleName(), handlerName,
                    InvocationHandler.class.getName()));
        }
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = this.originBeans.get(beanName);
        return origin != null ? origin : bean;
    }



}
