import request from '@/utils/request'

export function getJobLogList(params) {
  return request({ url: '/system/job/log/list', method: 'get', params })
}

export function deleteJobLog(id) {
  return request({ url: `/system/job/log/${id}`, method: 'delete' })
}
