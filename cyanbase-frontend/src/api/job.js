import request from '@/utils/request'

/**
 * 获取定时任务列表
 */
export function getJobList(params) {
  return request({
    url: '/system/job/list',
    method: 'get', // 保持 GET
    params: {
      pageNum: params.current,
      pageSize: params.size,
      jobName: params.jobName,
      jobGroup: params.jobGroup,
      status: params.status
    }
  })
}

/**
 * 获取定时任务详情
 */
export function getJobById(id) {
  return request({
    url: `/system/job/${id}`,
    method: 'get'
  })
}

/**
 * 新增定时任务
 */
export function addJob(data) {
  return request({
    url: '/system/job',
    method: 'post',
    data
  })
}

/**
 * 编辑定时任务
 */
export function updateJob(data) {
  return request({
    url: '/system/job',
    method: 'put',
    data
  })
}

/**
 * 删除定时任务
 */
export function deleteJob(id) {
  return request({
    url: `/system/job/${id}`,
    method: 'delete'
  })
}

/**
 * 执行定时任务
 */
export function executeJob(id) {
  return request({
    url: `/system/job/execute/${id}`,
    method: 'post'
  })
}

/**
 * 暂停定时任务
 */
export function pauseJob(id) {
  return request({
    url: `/system/job/pause/${id}`,
    method: 'post'
  })
}

/**
 * 恢复定时任务
 */
export function resumeJob(id) {
  return request({
    url: `/system/job/resume/${id}`,
    method: 'post'
  })
}

/**
 * 获取任务日志列表
 */
export function getJobLogList(params) {
  return request({
    url: '/system/job/log/list',
    method: 'get',
    params
  })
}
