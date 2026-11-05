MariaDB [cyan_base]> SELECT id, tenant_name, tenant_code FROM sys_tenant WHERE tenant_name LIKE '安辰实业';
id      tenant_name     tenant_code
3       安辰实业        TEST003
MariaDB [cyan_base]> SELECT tm.menu_id, m.menu_name, m.perms
-> FROM sys_tenant_menu tm
-> JOIN sys_menu m ON tm.menu_id = m.id
-> WHERE tm.tenant_id = 3
-> ORDER BY tm.menu_id;
menu_id menu_name       perms
1       系统管理
2       系统监控
3       业务工具
4       岗位管理
5       字典管理
6       参数配置
7       公告管理
8       在线用户
9       定时任务
10      操作日志
11      登录日志
14      消息管理
16      工作流定义      workflow:definition:list
17      表单设计        form:definition:list
19      岗位新增        system:post:add
20      岗位修改        system:post:edit
21      岗位删除        system:post:delete
22      字典新增        system:dict:add
23      字典修改        system:dict:edit
24      字典删除        system:dict:delete
25      强退用户        monitor:online:kick
26      任务执行        monitor:job:start
27      任务暂停        monitor:job:pause
28      接口监控
29      用户管理
30      角色管理        system:role:list
31      菜单管理
32      用户新增        system:user:add
33      用户修改        system:user:edit
34      用户删除        system:user:delete
35      角色新增        system:role:add
36      角色修改        system:role:edit
37      角色删除        system:role:delete
38      菜单新增        system:menu:add
39      菜单修改        system:menu:edit
40      菜单删除        system:menu:delete
41      部门管理
43      租户管理
54      三重一大
55      采购事项        sydyd:item:list
56      会议管理        sydyd:meeting:list
57      实施落地        sydyd:execute:list
58      制度管理        sydyd:system:list
59      清单管理        sydyd:list:list
60      议题管理        sydyd:topic:list
62      我的待办        workflow:todo:list
63      我的已办        workflow:done:list
64      发起流程        workflow:start
65      工作流管理
66      部门新增        system:dept:add
67      部门修改        system:dept:edit
68      部门删除        system:dept:delete
72      参数新增        system:config:add
73      参数修改        system:config:edit
74      参数删除        system:config:delete
75      通知新增        system:notice:add
76      通知修改        system:notice:edit
77      通知删除        system:notice:delete
78      流程新增        workflow:definition:save
79      流程修改        workflow:definition:save
80      流程删除        workflow:definition:delete
81      流程发布下线    workflow:definition:publish
82      流程审批        workflow:approve
83      流程转办        workflow:transfer
84      任务新增        monitor:job:add
85      任务修改        monitor:job:edit
86      任务删除        monitor:job:delete
87      日志删除        monitor:operlog:delete
88      日志删除        monitor:logininfor:delete
90      制度新增        sydyd:system:add
91      制度修改        sydyd:system:edit
92      制度删除        sydyd:system:remove
93      清单新增        sydyd:list:add
94      清单修改        sydyd:list:edit
95      清单删除        sydyd:list:remove
96      议题新增        sydyd:topic:add
97      议题修改        sydyd:topic:edit
98      议题删除        sydyd:topic:remove
99      事项新增        sydyd:item:add
100     事项修改        sydyd:item:edit
101     事项删除        sydyd:item:remove
102     会议新增        sydyd:meeting:add
103     会议修改        sydyd:meeting:edit
104     会议删除        sydyd:meeting:remove
105     实施新增        sydyd:execute:add
106     实施修改        sydyd:execute:edit
107     实施删除        sydyd:execute:remove
108     字典查询        system:dict:query
109     表单查询        form:definition:query
110     表单新增        form:definition:save
111     表单修改        form:definition:save
112     表单删除        form:definition:delete
113     表单流程绑定    workflow:binding:list
115     绑定查询        workflow:binding:query
116     绑定新增        workflow:binding:add
117     绑定修改        workflow:binding:edit
118     绑定删除        workflow:binding:delete
119     我的表单        workflow:myform:list
120     表单列表        workflow:myform:list
121     填报发起        workflow:myform:start
122     租户新增        system:tenant:add
123     租户修改        system:tenant:edit
124     租户删除        system:tenant:delete
127     发送消息        system:message:send
129     部门列表        system:dept:list
130     用户列表        system:user:list
131     岗位列表        system:post:list
132     字典列表        system:dict:list
133     参数列表        system:config:list
134     公告列表        system:notice:list
135     在线列表        monitor:online:list
136     任务列表        monitor:job:list
137     日志列表        monitor:operlog:list
138     登录列表        monitor:logininfor:list
139     消息列表        system:message:list
140     监控列表        monitor:api:list
141     菜单列表        system:menu:list
142     租户列表        system:tenant:list
143     角色列表        system:role:list
144     流程列表        workflow:definition:list
146     表单列表        form:definition:list
147     事项列表        sydyd:item:list
148     会议列表        sydyd:meeting:list
149     实施列表        sydyd:execute:list
150     制度列表        sydyd:system:list
151     清单列表        sydyd:list:list
152     议题列表        sydyd:topic:list
155     流程查询        workflow:definition:query
156     重置密码        system:user:resetPassword
158     清空操作日志    monitor:operlog:clean
159     清空登录日志    monitor:logininfor:clean
160     表单预览        form:definition:preview
161     表单设计        form:definition:designer
162     流程预览        workflow:definition:preview
163     流程设计        workflow:definition:designer
164     流程分发        workflow:definition:distribute
165     流程已分发查看  workflow:definition:distributeView
166     表单分发        form:definition:distribute
167     表单已分发查看  form:definition:distributeView
168     API监控修改     monitor:api:edit
169     API监控删除     monitor:api:delete
MariaDB [cyan_base]> SELECT id FROM sys_role WHERE role_name LIKE '中层';
id
27
MariaDB [cyan_base]> SELECT rm.menu_id, m.menu_name, m.perms
-> FROM sys_role_menu rm
-> JOIN sys_menu m ON rm.menu_id = m.id
-> WHERE rm.role_id IN (
->     SELECT id FROM sys_role WHERE role_name LIKE '中层'
-> )
-> ORDER BY rm.menu_id;
menu_id menu_name       perms
1       系统管理
2       系统监控
3       业务工具
4       岗位管理
5       字典管理
6       参数配置
7       公告管理
8       在线用户
9       定时任务
10      操作日志
11      登录日志
14      消息管理
16      工作流定义      workflow:definition:list
17      表单设计        form:definition:list
19      岗位新增        system:post:add
20      岗位修改        system:post:edit
21      岗位删除        system:post:delete
22      字典新增        system:dict:add
23      字典修改        system:dict:edit
24      字典删除        system:dict:delete
25      强退用户        monitor:online:kick
26      任务执行        monitor:job:start
27      任务暂停        monitor:job:pause
28      接口监控
29      用户管理
30      角色管理        system:role:list
31      菜单管理
32      用户新增        system:user:add
33      用户修改        system:user:edit
34      用户删除        system:user:delete
35      角色新增        system:role:add
36      角色修改        system:role:edit
37      角色删除        system:role:delete
38      菜单新增        system:menu:add
39      菜单修改        system:menu:edit
40      菜单删除        system:menu:delete
41      部门管理
43      租户管理
54      三重一大
55      采购事项        sydyd:item:list
56      会议管理        sydyd:meeting:list
57      实施落地        sydyd:execute:list
58      制度管理        sydyd:system:list
59      清单管理        sydyd:list:list
60      议题管理        sydyd:topic:list
62      我的待办        workflow:todo:list
63      我的已办        workflow:done:list
64      发起流程        workflow:start
65      工作流管理
66      部门新增        system:dept:add
67      部门修改        system:dept:edit
68      部门删除        system:dept:delete
72      参数新增        system:config:add
73      参数修改        system:config:edit
74      参数删除        system:config:delete
75      通知新增        system:notice:add
76      通知修改        system:notice:edit
77      通知删除        system:notice:delete
78      流程新增        workflow:definition:save
79      流程修改        workflow:definition:save
80      流程删除        workflow:definition:delete
81      流程发布下线    workflow:definition:publish
82      流程审批        workflow:approve
83      流程转办        workflow:transfer
84      任务新增        monitor:job:add
85      任务修改        monitor:job:edit
86      任务删除        monitor:job:delete
87      日志删除        monitor:operlog:delete
88      日志删除        monitor:logininfor:delete
90      制度新增        sydyd:system:add
91      制度修改        sydyd:system:edit
92      制度删除        sydyd:system:remove
93      清单新增        sydyd:list:add
94      清单修改        sydyd:list:edit
95      清单删除        sydyd:list:remove
96      议题新增        sydyd:topic:add
97      议题修改        sydyd:topic:edit
98      议题删除        sydyd:topic:remove
99      事项新增        sydyd:item:add
100     事项修改        sydyd:item:edit
101     事项删除        sydyd:item:remove
102     会议新增        sydyd:meeting:add
103     会议修改        sydyd:meeting:edit
104     会议删除        sydyd:meeting:remove
105     实施新增        sydyd:execute:add
106     实施修改        sydyd:execute:edit
107     实施删除        sydyd:execute:remove
108     字典查询        system:dict:query
109     表单查询        form:definition:query
110     表单新增        form:definition:save
111     表单修改        form:definition:save
112     表单删除        form:definition:delete
113     表单流程绑定    workflow:binding:list
115     绑定查询        workflow:binding:query
116     绑定新增        workflow:binding:add
117     绑定修改        workflow:binding:edit
118     绑定删除        workflow:binding:delete
119     我的表单        workflow:myform:list
120     表单列表        workflow:myform:list
121     填报发起        workflow:myform:start
122     租户新增        system:tenant:add
123     租户修改        system:tenant:edit
124     租户删除        system:tenant:delete
127     发送消息        system:message:send
129     部门列表        system:dept:list
130     用户列表        system:user:list
131     岗位列表        system:post:list
132     字典列表        system:dict:list
133     参数列表        system:config:list
134     公告列表        system:notice:list
135     在线列表        monitor:online:list
136     任务列表        monitor:job:list
137     日志列表        monitor:operlog:list
138     登录列表        monitor:logininfor:list
139     消息列表        system:message:list
140     监控列表        monitor:api:list
141     菜单列表        system:menu:list
142     租户列表        system:tenant:list
143     角色列表        system:role:list
144     流程列表        workflow:definition:list
146     表单列表        form:definition:list
147     事项列表        sydyd:item:list
148     会议列表        sydyd:meeting:list
149     实施列表        sydyd:execute:list
150     制度列表        sydyd:system:list
151     清单列表        sydyd:list:list
152     议题列表        sydyd:topic:list
155     流程查询        workflow:definition:query
156     重置密码        system:user:resetPassword
158     清空操作日志    monitor:operlog:clean
159     清空登录日志    monitor:logininfor:clean
160     表单预览        form:definition:preview
161     表单设计        form:definition:designer
162     流程预览        workflow:definition:preview
163     流程设计        workflow:definition:designer
164     流程分发        workflow:definition:distribute
165     流程已分发查看  workflow:definition:distributeView
166     表单分发        form:definition:distribute
167     表单已分发查看  form:definition:distributeView
168     API监控修改     monitor:api:edit
169     API监控删除     monitor:api:delete
MariaDB [cyan_base]> SELECT rm.menu_id, m.menu_name
-> FROM sys_role_menu rm
-> LEFT JOIN sys_tenant_menu tm ON rm.menu_id = tm.menu_id AND tm.tenant_id = 3
-> JOIN sys_menu m ON rm.menu_id = m.id
-> WHERE tm.menu_id IS NULL
-> AND rm.role_id IN (SELECT id FROM sys_role WHERE role_name LIKE '中层');
MariaDB [cyan_base]> SELECT * FROM sys_tenant_menu WHERE tenant_id = 3 ORDER BY menu_id;
id      tenant_id       menu_id create_by       update_by       create_time     update_time     del_flag        v_uk_tenant_menu
4876    3       1                       2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,1
4898    3       2                       2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,2
4806    3       3                       2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,3
4796    3       4                       2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,4
4887    3       5                       2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,5
4893    3       6                       2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,6
4801    3       7                       2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,7
4899    3       8                       2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,8
4902    3       9                       2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,9
4909    3       10                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,10
4913    3       11                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,11
4807    3       14                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,14
4821    3       16                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,16
4810    3       17                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,17
4798    3       19                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,19
4799    3       20                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,20
4800    3       21                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,21
4890    3       22                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,22
4891    3       23                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,23
4892    3       24                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,24
4901    3       25                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,25
4904    3       26                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,26
4905    3       27                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,27
4917    3       28                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,28
4785    3       29                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,29
4791    3       30                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,30
4882    3       31                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,31
4788    3       32                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,32
4789    3       33                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,33
4790    3       34                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,34
4793    3       35                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,35
4794    3       36                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,36
4795    3       37                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,37
4884    3       38                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,38
4885    3       39                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,39
4886    3       40                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,40
4780    3       41                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,41
4877    3       43                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,43
4845    3       54                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,54
4846    3       55                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,55
4856    3       56                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,56
4866    3       57                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,57
4851    3       58                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,58
4861    3       59                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,59
4871    3       60                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,60
4832    3       62                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,62
4835    3       63                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,63
4836    3       64                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,64
4820    3       65                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,65
4782    3       66                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,66
4783    3       67                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,67
4784    3       68                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,68
4895    3       72                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,72
4896    3       73                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,73
4897    3       74                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,74
4803    3       75                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,75
4804    3       76                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,76
4805    3       77                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,77
4824    3       78                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,78
4825    3       79                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,79
4826    3       80                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,80
4827    3       81                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,81
4833    3       82                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,82
4834    3       83                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,83
4906    3       84                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,84
4907    3       85                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,85
4908    3       86                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,86
4911    3       87                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,87
4915    3       88                      2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,88
4853    3       90                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,90
4854    3       91                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,91
4855    3       92                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,92
4863    3       93                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,93
4864    3       94                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,94
4865    3       95                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,95
4873    3       96                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,96
4874    3       97                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,97
4875    3       98                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,98
4848    3       99                      2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,99
4849    3       100                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,100
4850    3       101                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,101
4858    3       102                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,102
4859    3       103                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,103
4860    3       104                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,104
4868    3       105                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,105
4869    3       106                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,106
4870    3       107                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,107
4888    3       108                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,108
4811    3       109                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,109
4813    3       110                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,110
4814    3       111                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,111
4815    3       112                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,112
4837    3       113                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,113
4838    3       115                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,115
4839    3       116                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,116
4840    3       117                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,117
4841    3       118                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,118
4842    3       119                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,119
4843    3       120                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,120
4844    3       121                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,121
4879    3       122                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,122
4880    3       123                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,123
4881    3       124                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,124
4809    3       127                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,127
4781    3       129                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,129
4786    3       130                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,130
4797    3       131                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,131
4889    3       132                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,132
4894    3       133                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,133
4802    3       134                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,134
4900    3       135                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,135
4903    3       136                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,136
4910    3       137                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,137
4914    3       138                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,138
4808    3       139                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,139
4918    3       140                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,140
4883    3       141                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,141
4878    3       142                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,142
4792    3       143                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,143
4822    3       144                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,144
4812    3       146                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,146
4847    3       147                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,147
4857    3       148                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,148
4867    3       149                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,149
4852    3       150                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,150
4862    3       151                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,151
4872    3       152                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,152
4823    3       155                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,155
4787    3       156                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,156
4912    3       158                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,158
4916    3       159                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,159
4816    3       160                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,160
4817    3       161                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,161
4828    3       162                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,162
4829    3       163                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,163
4830    3       164                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,164
4831    3       165                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,165
4818    3       166                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,166
4819    3       167                     2026-11-03 23:58:03     2026-11-03 23:58:03     0       3,167
4919    3       168                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,168
4920    3       169                     2026-11-03 23:58:20     2026-11-03 23:58:20     0       3,169
MariaDB [cyan_base]> 