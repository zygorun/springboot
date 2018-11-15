package com.gorun.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebMvcConfigurerAdapter implements WebMvcConfigurer {
    //静态资源默认访问的路径为classpath:META-INF/resources/,classpath:resources/,
    // classpath:static/,classpath:static/
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        http://localhost:8080/gorun/people.jpg
//        gorun代表了"classpath:/static/my/"目录,addResourceLocations指的是文件放置的目录,
// addResoureHandler指的是对外暴露的访问路径
        registry.addResourceHandler("/gorun/**").addResourceLocations(
                "classpath:/static/my/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
//        直接访问到login.jsp,省去在控制器中的代码
        registry.addViewController("/toLogin").setViewName("login");
    }
}
