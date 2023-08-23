package myspringframe;

import myspringframe.annotation.Component;

@Component
public class test1 {
    public int a = 1;
    public int b=2;
    public test1(){
        a =1;
        b = 999;
    }
    public int getA(){
        return b;
    }
}
