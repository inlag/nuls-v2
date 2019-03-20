package io.nuls.test.cases;

import io.nuls.tools.core.ioc.SpringLiteContext;

/**
 * @Author: zhoulijun
 * @Time: 2019-03-20 10:18
 * @Description: 功能描述
 */
public abstract class TestCaseChain implements TestCaseIntf<Object,Object> {

    public abstract Class<? extends TestCaseIntf>[] testChain();

    @Override
    public Object doTest(Object param,int depth) throws TestFailException {
        Class<? extends TestCaseIntf>[] testCases = this.testChain();
        for (Class tc: testCases) {
            TestCaseIntf t = (TestCaseIntf) SpringLiteContext.getBean(tc);
            param = t.check(param,depth);
        }
        return param;
    }

}