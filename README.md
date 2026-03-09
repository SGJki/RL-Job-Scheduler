this is a web Service for RL training manager 

这个项目不仅仅是用来练手的，它的目标是 能够写在简历上，证明你懂“后端架构”和“AI工程化” 。
1. 核心业务痛点 (你的故事线)
   你发现在实验室里，大家跑 RL 实验非常混乱：

- 手动 SSH 到服务器跑脚本，没人记录参数。
- 多个人抢 GPU，经常把别人的进程 kill 掉。
- 跑完的结果（TensorBoard logs、模型 checkpoints）散落在各处，很难对比。
  解决方案 ：开发一个 Web 平台，统一管理训练任务、调度计算资源、可视化结果。
