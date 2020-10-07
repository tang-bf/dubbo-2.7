package com.luban.dubbo_spi;

import org.apache.dubbo.common.URL;

/**
 * @ProjectName: dubbo-2.7.x
 * @Package: com.luban.dubbo_spi
 * @ClassName: CarWrapper
 * @Description:
 * @Author: tbf
 * @CreateDate: 2020-10-07 15:58
 * @UpdateUser: Administrator
 * @UpdateDate: 2020-10-07 15:58
 * @UpdateRemark:
 * @Version: 1.0
 */

public class CarWrapper implements  Car{
    public  Car car;

    public CarWrapper(Car car) {
        this.car = car;
    }

    @Override
    public void test(URL url) {
        System.out.println("wrapper  start");
        car.test(null);
        System.out.println("wrapper end ");
    }
}
