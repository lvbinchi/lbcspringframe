package myspringframe.aop;

import myspringframe.Utils.t2;
import myspringframe.annotation.ComponentScan;
import myspringframe.annotation.Import;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

@ComponentScan
@Import({t2.class})
public class ProxyResolver
{
    private static ProxyResolver INSTANCE = null;

    ByteBuddy byteBuddy = new ByteBuddy();
    public ProxyResolver(){}
    public <T> T createProxy(T bean, InvocationHandler handler) {
        Class<?> targetClass = bean.getClass();
        Class<?> proxyClass = this.byteBuddy
                // 子类用默认无参数构造方法:
                .subclass(targetClass, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                // 拦截所有public方法:
                .method(ElementMatchers.isPublic()).intercept(InvocationHandlerAdapter.of(
                        // 新的拦截器实例:
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                // 将方法调用代理至原始Bean:
                                return handler.invoke(bean, method, args);
                            }
                        }))
                // 生成字节码:
                .make()
                // 加载字节码:
                .load(targetClass.getClassLoader()).getLoaded();
        Object proxy;
        try {
            proxy = proxyClass.getConstructor().newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return (T) proxy;
    }

    public static ProxyResolver getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ProxyResolver();
        }
        return INSTANCE;
    }


}
