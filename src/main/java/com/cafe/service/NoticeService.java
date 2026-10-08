package com.cafe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cafe.common.BizException;
import com.cafe.common.Constants;
import com.cafe.entity.Notice;
import com.cafe.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告服务。
 *
 * 公告支持撤下和逻辑删除：历史公告可能已作为计费调整、活动的依据，
 * 直接删除会导致追溯不到当时发布的内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    private Notice lockById(Long id) {
        Notice notice = noticeMapper.selectForUpdate(id);
        if (notice == null || Integer.valueOf(-1).equals(notice.getStatus())) throw new BizException("公告不存在");
        return notice;
    }

    /** 已发布的公告，会员端与收银员端展示 */
    public List<Notice> listPublished() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, Constants.ENABLED)
                .orderByDesc(Notice::getCreateTime)
                .orderByDesc(Notice::getId));
    }

    /** 管理端分页查询全部公告 */
    public IPage<Notice> page(long pageNo, long pageSize, String keyword) {
        return noticeMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<Notice>()
                        .ne(Notice::getStatus, -1)
                        .like(keyword != null && !keyword.isBlank(), Notice::getTitle, keyword)
                        .orderByDesc(Notice::getCreateTime)
                        .orderByDesc(Notice::getId));
    }

    public Notice getById(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null || Integer.valueOf(-1).equals(notice.getStatus())) {
            throw new BizException("公告不存在，ID=" + id);
        }
        return notice;
    }

    @Transactional(rollbackFor = Exception.class)
    public Notice publish(String title, String content, Long publisherId) {
        if (title == null || title.isBlank()) {
            throw new BizException("公告标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new BizException("公告内容不能为空");
        }
        Notice notice = new Notice();
        notice.setTitle(title.trim());
        notice.setContent(content.trim());
        notice.setPublisherId(publisherId);
        notice.setStatus(Constants.ENABLED);
        noticeMapper.insert(notice);
        log.info("公告发布 标题={} 发布人={}", title, publisherId);
        return notice;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String title, String content) {
        Notice notice = lockById(id);
        if (title != null && !title.isBlank()) {
            notice.setTitle(title.trim());
        }
        if (content != null && !content.isBlank()) {
            notice.setContent(content.trim());
        }
        notice.setUpdateTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long operatorId) {
        lockById(id);
        noticeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notice>()
                .eq(Notice::getId, id).set(Notice::getStatus, -1).set(Notice::getUpdateTime, LocalDateTime.now()));
        log.info("公告删除 ID={} 操作人={}", id, operatorId);
    }

    /** 发布 / 撤下公告 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, int status) {
        Notice notice = lockById(id);
        if (status != Constants.ENABLED && status != Constants.DISABLED) throw new BizException("公告状态无效");
        notice.setStatus(status);
        notice.setUpdateTime(LocalDateTime.now());
        noticeMapper.updateById(notice);
        log.info("公告状态变更 标题={} -> {}", notice.getTitle(), status);
    }
}
