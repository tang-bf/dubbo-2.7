package com.luban.dubbo_spi;

import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * @ProjectName: dubbo-2.7.x
 * @Package: com.luban.dubbo_spi
 * @ClassName: SpiTest
 * @Description:
 * @Author: tbf
 * @CreateDate: 2020-10-07 15:51
 * @UpdateUser: Administrator
 * @UpdateDate: 2020-10-07 15:51
 * @UpdateRemark:
 * @Version: 1.0
 */

public class SpiTest {
    public static void main(String[] args) {
        ExtensionLoader<Car> extensionLoader = ExtensionLoader.getExtensionLoader(Car.class);
        Car car1 = extensionLoader.getExtension("car1");
        // 先定义warpper类， 这个类必须实现SPI接口
        //warpper类必须以wrapper 结尾命名
        //warpper类的构造方法必须传入SPI接口的参数，
        //Wrapper类必须加入META-INF/dubbo目录下的SPI接口的实现文件中
        //Wrapper类方法的调用顺序是根据META-INF/dubbo目录下的SPI接口的实现文件中顺序配置来的。
        // 在文件上面的先调用，后面的后调用  有bug，run  debug的时候执行顺序不一致
        //Wrapper 类上可以使用@Wrapper注解指定对特点的SPI扩展生效。
        //
        // 多个wrapper类会排序  类似spring中的order
        // wrapperClassesList.addAll(cachedWrapperClasses);
        //    wrapperClassesList.sort(WrapperComparator.COMPARATOR);
        //   Collections.reverse(wrapperClassesList);
//       为什么debug调试时候是先执行了
//       wrapper  start
//        wrapper2  start
//        test
//        wrapper2 end
//        wrapper end
        //注释掉日志信息 直接run的时候执行顺序如下
        /*wrapper2  start
wrapper  start
test
wrapper end
wrapper2 end
         */
       car1.test(null);
    }
}
