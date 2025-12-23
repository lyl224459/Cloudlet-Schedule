package CloudletScheduler.datacenter;
/* *
 * @description: 多目标优化函数接口
 */
public interface OptFunctionMulti {
    ObjectiveValues evaluate(int[] params);
}