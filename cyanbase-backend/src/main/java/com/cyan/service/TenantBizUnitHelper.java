package com.cyan.service;

import com.cyan.entity.SysDept;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 租户内经营单元组织树辅助工具。
 * <p>
 * 封装两类能力：
 * <ol>
 *   <li>向上递归查找最近经营单元（nearestBizUnitOf）—— 含 Redis 缓存</li>
 *   <li>向下展开经营单元自身 + 全部子部门（collectBizUnitAndChildren）—— DataScope 覆盖模式用</li>
 * </ol>
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>Redis key: bizunit:nearest:{tenantId}:{deptId}，TTL 30 分钟</li>
 *   <li>组织树结构 / 经营单元标记变更时，由 Controller / Service 在写操作后主动清掉相关 key</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBizUnitHelper {

    private static final String CACHE_KEY_PREFIX = "bizunit:nearest:";
    private static final long CACHE_TTL_MINUTES = 30L;

    private final SysDeptService deptService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 向上递归查找距离指定部门最近的经营单元节点。
     * <p>
     * 节点自身是经营单元则直接返回自身 ID。
     * </p>
     *
     * @param deptId   任意部门ID
     * @param tenantId 租户ID（用于缓存 key 隔离）
     * @return 最近的经营单元部门ID，找不到返回 null
     */
    public Long nearestBizUnitOf(Long deptId, Long tenantId) {
        if (deptId == null || tenantId == null) return null;

        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + deptId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return "null".equals(cached) ? null : Long.valueOf(cached);
            }
        } catch (Exception e) {
            log.debug("[BizUnitHelper] Redis 读取失败，降级查DB: {}", e.getMessage());
        }

        Long result = doNearestBizUnit(deptId);

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey, result != null ? String.valueOf(result) : "null",
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("[BizUnitHelper] Redis 写入失败: {}", e.getMessage());
        }
        return result;
    }

    /** 递归查（无缓存路径） */
    private Long doNearestBizUnit(Long deptId) {
        SysDept dept = deptService.getById(deptId);
        if (dept == null) return null;

        if (Integer.valueOf(1).equals(dept.getIsBizUnit())) {
            return deptId; // 自身就是经营单元
        }

        // 向上找 parentId
        Long parentId = dept.getParentId();
        if (parentId == null || parentId == 0L) {
            return null; // 到根了
        }
        return doNearestBizUnit(parentId);
    }

    /**
     * 展开经营单元自身 + 所有子部门。
     * <p>
     * 用于 DataScope 覆盖模式构造 dept_id IN (...) 条件。
     * 调用方需先确保 bizUnitId 本身是合法经营单元。
     * </p>
     */
    public List<Long> collectBizUnitAndChildren(Long bizUnitId) {
        if (bizUnitId == null) return Collections.emptyList();
        List<Long> result = new ArrayList<>();
        result.add(bizUnitId);
        collectChildren(bizUnitId, result);
        return result;
    }

    private void collectChildren(Long parentId, List<Long> bucket) {
        List<SysDept> children = deptService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getParentId, parentId));
        if (children == null || children.isEmpty()) return;
        for (SysDept c : children) {
            bucket.add(c.getId());
            collectChildren(c.getId(), bucket);
        }
    }

    /**
     * 清理指定租户下所有 nearest 缓存。
     * <p>
     * 在部门树结构或 is_biz_unit 标记变更时调用。
     * </p>
     */
    public void clearCacheByTenant(Long tenantId) {
        if (tenantId == null) return;
        try {
            String pattern = CACHE_KEY_PREFIX + tenantId + ":*";
            var keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("[BizUnitHelper] cleared nearest cache for tenantId={}, count={}", tenantId, keys.size());
            }
        } catch (Exception e) {
            log.warn("[BizUnitHelper] clearCacheByTenant failed: {}", e.getMessage());
        }
    }

    /**
     * 清理单个部门的 nearest 缓存。
     */
    public void clearCacheOne(Long tenantId, Long deptId) {
        if (tenantId == null || deptId == null) return;
        try {
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + tenantId + ":" + deptId);
        } catch (Exception ignored) {}
    }
}
