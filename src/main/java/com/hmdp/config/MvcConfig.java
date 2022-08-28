package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.MyInterceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author L.J.L
 * @QQ 963314043
 * @date 2022/8/26 13:30
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new MyInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0);


       registry.addInterceptor(new LoginInterceptor())
               .excludePathPatterns(
                       "/user/code",
                       "/user/login",
                       "/blog/hot",
                       "/shop/**",
                       "/shop-type/**",
                       "/voucher/**"
               ).order(1);
       //order越大 优先级越低

    }
}
