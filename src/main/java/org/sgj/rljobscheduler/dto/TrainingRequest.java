package org.sgj.rljobscheduler.dto;

/**
 * 训练请求参数
 * 对应前端传来的 JSON:
 * {
 *   "algorithm": "PPO",
 *   "episodes": 1000,
 *   "learningRate": 0.001
 * }
 */
public class TrainingRequest {
    private String algorithm;
    private int episodes;
    private double learningRate;

    // 必须有无参构造函数 (Jackson 反序列化需要)
    public TrainingRequest() {}

//    public TrainingRequest(String algorithm, Integer episodes, double learningRate) {
//        this.algorithm = algorithm;
//        this.episodes = (episodes ==  null) ? 1 : episodes;
//        this.learningRate = learningRate;
//        System.out.println(">>> Fuck: " + algorithm);
//    }

    // Getters and Setters
    public String getAlgorithm() {
        System.out.println(">>> [Getter] getAlgorithm 被调用: " + algorithm);
        return algorithm; }

    public void setAlgorithm(String algorithm) { this.algorithm = algorithm;
        System.out.println(">>> [Setter] setAlgorithm 被调用: " + algorithm);
    }

    public Integer getEpisodes() { return episodes; }
    public void setEpisodes(Integer episodes) {
        System.out.println(">>> [Setter] setEpisodes 被调用: " + episodes);
        this.episodes = (episodes == null) ? 0 : episodes; }

    public Double getLearningRate() { return learningRate; }
    public void setLearningRate(Double learningRate) {
        System.out.println(">>> [Setter] setLearningRate 被调用: " + learningRate);
        this.learningRate =  (learningRate == null) ? 0.0 : learningRate; }
}
