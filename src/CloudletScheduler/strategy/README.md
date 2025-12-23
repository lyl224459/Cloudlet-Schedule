# strategy包
存放的是函数改进策略的通用方法  

1. chaosMap类  
存放混沌映射函数（21种）  
   - tentMap
   - chebyshevMap
   - circleMap
   - gaussMouseMap
   - iterativeMap
   - logisticMap
   - percentMap
   - sineMap
   - singerlMap
   - sinusoidalMap
   - fuchMap
   - SPMMap
   - ICMICmap
   - tentLogisticCosineMap
   - sineTentCosineMap
   - logisticSineCosineMap
   - henonMap（TODO待实现）
   - logisticTentMap
   - bernoulliMap
   - kenkMap

2. mutaions类  
存放变异策略（13种）
    - applyGaussianMutation
    - applyGaussianEliteMutation
    - applyCauchyMutation
    - applyNCauchyMutation
    - applyTMutation
    - applyDEBest1Mutation
    - randToBest
    - TODO ......