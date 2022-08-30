package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class  BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(int id) {
        // 根据用户查询

        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLike(blog);
        return  Result.ok(blog);

    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        //2.判断登录用户是否已经点赞
        String key = "blog:liked:"+id;
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score ==null) {
            //3.如果没有点赞 可以点赞
            //3.1  数据库点赞数加一
            boolean isSuccess =
                    update().setSql("liked= liked + 1").eq("id", id).update();

            //3.2 保存用户到Redis的set集合
            if (isSuccess){
                //stringRedisTemplate.opsForSet().add(key,userId.toString());
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //4. 如果已经点赞 取消点赞
            //4.1 数据库减一、
            //4.2 redis里的数据也要删除
            boolean isSuccess =
                    update().setSql("liked= liked - 1").eq("id", id).update();

            //3.2 保存用户到Redis的set集合
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isBlogLike(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:"+id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
       if (top5 == null || top5.isEmpty()){
           return Result.ok(Collections.emptyList());
       }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        List<UserDTO> collect = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collect);
    }

    @Override
    public Result saveBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        boolean success = save(blog);
        if (!success){
            return Result.fail("新增失败");
        }

        //查询所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();

        for(Follow follow : follows){
            Long userId1 = follow.getUserId();

            String key = "feed:" + userId1;
            stringRedisTemplate.opsForZSet()
                    .add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = "feed:" + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4.解析数据：blogId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 2
        int os = 1; // 2
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) { // 5 4 4 2 2
            // 4.1.获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 4.2.获取分数(时间戳）
            long time = tuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }

        // 5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.查询blog有关的用户
            Long uid = blog.getUserId();
            User user = userService.getById(uid);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isBlogLike(blog);
        }

        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    public void isBlogLike(Blog blog){
        //1.获取当前登录的用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        //2.判断登录用户是否已经点赞
        String key = "blog:liked:"+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
}
