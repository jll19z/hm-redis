package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.LOGIN_USER_TTL;

/**
 * @author L.J.L
 * @QQ 963314043
 * @date 2022/8/26 16:43
 */
public class MyInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public MyInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //System.out.println("refresh");
        //基于token来获取user
        String token = request.getHeader("authorization");
        //System.out.println(token);
        if (StrUtil.isBlank(token)) {
            return true;
        }
        Map<Object, Object> usermap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (usermap.isEmpty()) {
            return true;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(usermap, new UserDTO(), false);


//        HttpSession session = request.getSession();
//        Object user = session.getAttribute("user");

        UserHolder.saveUser(userDTO);


        //刷新token有效时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //System.out.println("refresh  out");
        UserHolder.removeUser();
    }
}
