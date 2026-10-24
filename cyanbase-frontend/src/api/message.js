import request from '@/utils/request'

/**
 * 获取未读消息数量
 */
export function getUnreadCount() {
  return request({
    url: '/system/message/unread/count',
    method: 'get'
  })
}

/**
 * 获取我的消息列表
 */
export function getMyMessageList(params) {
  return request({
    url: '/system/message/list',
    method: 'get',
    params
  })
}

/**
 * 获取消息详情
 */
export function getMessageById(id) {
  return request({
    url: `/system/message/${id}`,
    method: 'get'
  })
}

/**
 * 发送消息给指定用户（后端真实接口）
 */
export function addMessage(data) {
  return request({
    url: '/system/message/sendUser',
    method: 'post',
    data
  })
}

/**
 * 标记消息为已读
 */
export function markMessageRead(id) {
  return request({
    url: `/system/message/read/${id}`,
    method: 'put'
  })
}

/**
 * 全部标记为已读
 */
export function markAllRead() {
  return request({
    url: '/system/message/readAll',
    method: 'put'
  })
}

/**
 * 删除消息
 */
export function deleteMessage(id) {
  return request({
    url: `/system/message/${id}`,
    method: 'delete'
  })
}