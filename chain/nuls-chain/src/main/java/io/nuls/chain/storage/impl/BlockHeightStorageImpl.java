/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.chain.storage.impl;

import io.nuls.chain.model.po.BlockHeight;
import io.nuls.chain.storage.BlockHeightStorage;
import io.nuls.chain.util.LoggerUtil;
import io.nuls.db.service.RocksDBService;
import io.nuls.tools.basic.InitializingBean;
import io.nuls.tools.core.annotation.Component;
import io.nuls.tools.data.ByteUtils;
import io.nuls.tools.log.Log;

/**
 * @author lan
 * @description
 * @date 2019/02/20
 **/
@Component
public class BlockHeightStorageImpl implements BlockHeightStorage, InitializingBean {
    private final String TBL = "txsBlockHeight";


    /**
     * 该方法在所有属性被设置之后调用，用于辅助对象初始化
     * This method is invoked after all properties are set, and is used to assist object initialization.
     */
    @Override
    public void afterPropertiesSet() {
        try {
            if (!RocksDBService.existTable(TBL)) {
                RocksDBService.createTable(TBL);
            }
        } catch (Exception e) {
            Log.error(e);
        }
    }

    @Override
    public BlockHeight getBlockHeight(int chainId) {
        LoggerUtil.Log.info("chainId = {} getBlockHeight", chainId);
        byte[] stream = RocksDBService.get(TBL, ByteUtils.intToBytes(chainId));
        if (stream == null) {
            return null;
        }
        try {
            BlockHeight blockHeight = new BlockHeight();
            blockHeight.parse(stream, 0);
            return blockHeight;
        } catch (Exception e) {
            LoggerUtil.Log.error("getBlockHeight serialize error.", e);
        }
        return null;
    }

    @Override
    public void saveOrUpdateBlockHeight(int chainId, BlockHeight blockHeight) throws Exception {
        LoggerUtil.Log.info("chainId = {},blockHeight={} saveOrUpdateBlockHeight", chainId, blockHeight);
        RocksDBService.put(TBL, ByteUtils.intToBytes(chainId), blockHeight.serialize());
    }
}