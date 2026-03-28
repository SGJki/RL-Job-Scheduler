import argparse
import time
import sys
import random

def main():
    # 1. 接收命令行参数
    parser = argparse.ArgumentParser(description='Simulate RL Training')
    parser.add_argument('--taskId', type=str, required=True, help='Task ID')
    parser.add_argument('--algo', type=str, required=True, help='Algorithm Name')
    parser.add_argument('--episodes', type=int, required=True, help='Total Episodes')
    parser.add_argument('--lr', type=float, required=True, help='Learning Rate')

    args = parser.parse_args()

    # 2. 打印初始化日志 (会被 Java 捕获)
    print(f"[Python] Starting training for Task {args.taskId}")
    print(f"[Python] Algorithm: {args.algo}, Episodes: {args.episodes}, LR: {args.lr}")
    sys.stdout.flush() # 确保日志立即输出

    # 3. 模拟训练过程
    total_steps = args.episodes
    # total_steps = 5
    # for i in range(total_steps):
    for i in range(100):
        time.sleep(1)# 模拟耗时
        progress = (i + 1) / total_steps * 100
        print(f"[Python] Progress: {progress:.0f}%")
        sys.stdout.flush()

    # 4. 计算最终结果 (模拟)
    # 假设算法越复杂，随机奖励越高
    base_score = 100 if args.algo == 'PPO' else 80
    final_reward = base_score + random.uniform(-10, 10) * args.lr * 1000

    # 5. 输出最终结果 (关键！Java 会解析这一行)
    # 格式约定: "FINAL_REWARD:<数值>"
    print(f"FINAL_REWARD:{final_reward:.2f}")
    print("[Python] Training Completed.")

if __name__ == "__main__":
    main()