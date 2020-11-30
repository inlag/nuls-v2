/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.datasource.rocksdb;

import io.nuls.contract.config.ContractConfig;
import io.nuls.contract.util.Log;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.model.StringUtils;
import io.nuls.core.rockdb.manager.RocksDBManager;
import io.nuls.core.rockdb.service.BatchOperation;
import io.nuls.core.rockdb.service.RocksDBService;
import io.nuls.core.rockdb.util.DBUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.datasource.DbSettings;
import org.ethereum.datasource.DbSource;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.ethereum.util.ByteUtil.toHexString;

/**
 * @author Roman Mandeleil
 * @since 18.01.2015
 */
public class RocksDbDataSource implements DbSource<byte[]> {

    private static final Logger logger = LoggerFactory.getLogger("db");

    private String AREA;
    private String AREA_OLD;

    SystemProperties config = SystemProperties.getDefault(); // initialized for standalone test

    String name;
    boolean alive;
    RocksDB rocksDB;

    DbSettings settings = DbSettings.DEFAULT;

    // The native LevelDB insert/update/delete are normally thread-safe
    // However close operation is not thread-safe and may lead to a native crash when
    // accessing a closed DB.
    // The leveldbJNI lib has a protection over accessing closed DB but it is not synchronized
    // This ReadWriteLock still permits concurrent execution of insert/delete/update operations
    // however blocks them on init/close/delete operations
    private ReadWriteLock resetDbLock = new ReentrantReadWriteLock();

    public RocksDbDataSource() {
    }

    public RocksDbDataSource(int chainId) {
        this.AREA_OLD = "contract_" + chainId;
        this.AREA = AREA_OLD + "_";
    }

    public RocksDbDataSource(String name) {
        this.name = name;
        logger.debug("New RocksDbDataSource: " + name);
    }

    @Override
    public void init() {
        init(DbSettings.DEFAULT);
    }

    @Override
    public void init(DbSettings settings) {
        this.settings = settings;
        resetDbLock.writeLock().lock();
        try {
            logger.debug("~> RocksDbDataSource.init(): " + name);

            if (isAlive()) {
                return;
            }

            if (name == null) {
                throw new NullPointerException("no name set to the db");
            }


            String[] areas = RocksDBService.listTable();
            for (String area : areas) {
                if (AREA_OLD.equals(area)) {
                    RocksDBManager.closeTable(area);
                    break;
                }
            }
            initContractTables();
            alive = true;

            logger.debug("<~ RocksDbDataSource.init(): " + name);
        } catch (Exception e) {
            logger.error("RocksDbDataSource.init() error", e);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    private void initContractTables() {
        try {
            ContractConfig contractConfig = SpringLiteContext.getBean(ContractConfig.class);
            String dataPath = contractConfig.getDataPath();
            File pathDir = DBUtils.loadDataPath(dataPath);
            dataPath = pathDir.getPath();
            dataPath += File.separator + "smart-contract" + File.separator + "contracts";
            RocksDBManager.init(dataPath, customOptions());
            //boolean migrationData = true;
            String tableName;
            for (int i = 0; i < 129; i++) {
                tableName = AREA + i;
                if (RocksDBManager.getTable(tableName) != null) {
                    //migrationData = false;
                    continue;
                }
                RocksDBManager.createTable(tableName, customOptions());
            }
            //if (migrationData) {
            //    Log.info("MigrationData start...");
            //    migrationData();
            //    Log.info("MigrationData finished.");
            //}
        } catch (Exception e) {
            throw new RuntimeException("error create table: " + e.getMessage());
        }
    }

    private void migrationData() throws RocksDBException {
        byte[] key;
        byte[] value;
        byte first;
        Integer index;
        RocksDB table = RocksDBManager.getTable("contract_1");
        RocksDB _table;
        try (RocksIterator iterator = table.newIterator()) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
                key = iterator.key();
                value = iterator.value();
                first = key[0];
                index = Math.abs((int) first);
                _table = RocksDBManager.getTable(AREA + index);
                _table.put(key, value);
            }
        }
    }
    //private RocksDB createTable(String area) {
    //    try {
    //        if (StringUtils.isBlank(area)) {
    //            throw new RuntimeException("empty area");
    //        }
    //        ContractConfig contractConfig = SpringLiteContext.getBean(ContractConfig.class);
    //        String dataPath = contractConfig.getDataPath();
    //        File pathDir = DBUtils.loadDataPath(dataPath);
    //        dataPath = pathDir.getPath();
    //        dataPath += File.separator + "smart-contract";
    //        File dir = new File(dataPath + File.separator + area);
    //        if (!dir.exists()) {
    //            dir.mkdir();
    //        }
    //        dataPath = dataPath + File.separator + area + File.separator + "rocksdb";
    //        Log.info("Contract dataPath is " + dataPath);
    //
    //        Options options = new Options();
    //        options.setCreateIfMissing(true);
    //        /**
    //         * 优化读取性能方案
    //         */
    //        options.setAllowMmapReads(true);
    //        options.setCompressionType(CompressionType.NO_COMPRESSION);
    //        //options.setCompressionType(CompressionType.LZ4_COMPRESSION);
    //        //options.setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION);
    //        //options.setLevelCompactionDynamicLevelBytes(true);
    //        options.setMaxOpenFiles(-1);
    //
    //        BlockBasedTableConfig tableOption = new BlockBasedTableConfig();
    //        tableOption.setBlockSize(16 * 1024);
    //        tableOption.setBlockCache(new ClockCache(32 * 1024 * 1024));
    //        tableOption.setCacheIndexAndFilterBlocks(true);
    //        tableOption.setPinL0FilterAndIndexBlocksInCache(true);
    //        tableOption.setBlockRestartInterval(4);
    //        tableOption.setFilterPolicy(new BloomFilter(100, true));
    //        options.setTableFormatConfig(tableOption);
    //
    //        options.setMaxBackgroundCompactions(6);
    //        options.setNewTableReaderForCompactionInputs(true);
    //        //为压缩的输入，打开RocksDB层的预读取
    //        options.setCompactionReadaheadSize(128 * SizeUnit.KB);
    //        return RocksDB.open(options, dataPath);
    //    } catch (Exception e) {
    //        Log.error("error create table: " + area, e);
    //        throw new RuntimeException("error create table: " + area);
    //    }
    //}

    private Options customOptions() {
        Options options = new Options();
        options.setCreateIfMissing(true);
        /**
         * 优化读取性能方案
         */
        options.setAllowMmapReads(true);
        options.setCompressionType(CompressionType.NO_COMPRESSION);
        options.setMaxOpenFiles(-1);

        BlockBasedTableConfig tableOption = new BlockBasedTableConfig();
        tableOption.setBlockSize(16 * 1024);
        tableOption.setBlockCache(new ClockCache(32 * 1024 * 1024));
        tableOption.setCacheIndexAndFilterBlocks(true);
        tableOption.setPinL0FilterAndIndexBlocksInCache(true);
        tableOption.setBlockRestartInterval(4);
        tableOption.setFilterPolicy(new BloomFilter(100, true));
        options.setTableFormatConfig(tableOption);

        options.setMaxBackgroundCompactions(6);
        options.setNewTableReaderForCompactionInputs(true);
        //为压缩的输入，打开RocksDB层的预读取
        options.setCompactionReadaheadSize(128 * SizeUnit.KB);
        return options;
    }

    private RocksDB getRocksDB(byte[] key) {
        return RocksDBManager.getTable(AREA + getIndex(key));
    }

    private RocksDB getRocksDB(int index) {
        return RocksDBManager.getTable(AREA + index);
    }

    private int getIndex(byte[] key) {
        byte first = key[0];
        return Math.abs((int) first);
    }

    @Override
    public void reset() {
    }

    @Override
    public byte[] prefixLookup(byte[] key, int prefixBytes) {
        throw new RuntimeException("RocksDbDataSource.prefixLookup() is not supported");
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte[] get(byte[] key) {
        //long startTime = System.nanoTime();
        resetDbLock.readLock().lock();
        try {
            //if (logger.isTraceEnabled()) {
            //    logger.trace("~> RocksDbDataSource.get(): " + name + ", key: " + toHexString(key));
            //}
            try {

                byte[] ret = getRocksDB(key).get(key);
                //if (Log.isInfoEnabled()) {
                //    Log.info("<~ db.get(): " + name + ", key: " + toHexString(key) + ", " + (ret == null ? "null" : ret.length) + ", cost {}", System.nanoTime() - startTime);
                //}
                return ret;
            } catch (Exception e) {
                logger.warn("Exception. Retrying again...", e);
                byte[] ret = null;
                try {
                    ret = getRocksDB(key).get(key);
                } catch (RocksDBException ex) {
                    // skip it
                }
                //if (logger.isTraceEnabled()) {
                //    logger.trace("<~ RocksDbDataSource.get(): " + name + ", key: " + toHexString(key) + ", " + (ret == null ? "null" : ret.length));
                //}
                return ret;
            }
        } finally {
            resetDbLock.readLock().unlock();
        }
    }

    @Override
    public void put(byte[] key, byte[] value) {
        resetDbLock.writeLock().lock();
        try {
            //if (logger.isTraceEnabled()) {
            //    logger.trace("~> RocksDbDataSource.put(): " + name + ", key: " + toHexString(key) + ", " + (value == null ? "null" : value.length));
            //}
            getRocksDB(key).put(key, value);
            //if (logger.isInfoEnabled()) {
            //    logger.info("<~ RocksDbDataSource.put(): " + name + ", key: " + toHexString(key) + ", " + (value == null ? "null" : value.length));
            //}
        } catch (Exception e) {
            logger.error("RocksDbDataSource.put() error", e);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public void delete(byte[] key) {
        resetDbLock.writeLock().lock();
        try {
            //if (logger.isTraceEnabled()) {
            //    logger.trace("~> RocksDbDataSource.delete(): " + name + ", key: " + toHexString(key));
            //}
            getRocksDB(key).delete(key);
            //if (logger.isInfoEnabled()) {
            //    logger.info("<~ RocksDbDataSource.delete(): " + name + ", key: " + toHexString(key));
            //}
        } catch (Exception e) {
            logger.error("RocksDbDataSource.delete() error", e);
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public Set<byte[]> keys() {
        return null;
    }

    private void updateBatchInternal(Map<byte[], byte[]> rows) throws Exception {
        Map<Integer, WriteBatch> writeBatchMap = new HashMap<>();
        WriteBatch batch = null;
        try {
            Integer index;
            byte[] key, value;
            //batch = new WriteBatch();
            Set<Map.Entry<byte[], byte[]>> entrySet = rows.entrySet();
            for (Map.Entry<byte[], byte[]> entry : entrySet) {
                key = entry.getKey();
                value = entry.getValue();
                index = getIndex(key);
                batch = writeBatchMap.computeIfAbsent(index, (k) -> new WriteBatch());
                if (value == null) {
                    batch.delete(key);
                } else {
                    batch.put(key, value);
                }
            }
            int _index;
            Set<Map.Entry<Integer, WriteBatch>> entries = writeBatchMap.entrySet();
            for (Map.Entry<Integer, WriteBatch> entry : entries) {
                _index = entry.getKey();
                getRocksDB(_index).write(new WriteOptions(), entry.getValue());
            }
        } catch (Exception e) {
            throw e;
        } finally {
            // Make sure you close the batch to avoid resource leaks.
            // 关闭批量操作对象释放资源
            if (batch != null) {
                batch.close();
            }
        }
    }
    //private void updateBatchInternal(Map<byte[], byte[]> rows) throws Exception {
    //    WriteBatch batch = null;
    //    try {
    //        batch = new WriteBatch();
    //        Set<Map.Entry<byte[], byte[]>> entrySet = rows.entrySet();
    //        for (Map.Entry<byte[], byte[]> entry : entrySet) {
    //            if (entry.getValue() == null) {
    //                batch.delete(entry.getKey());
    //            } else {
    //                batch.put(entry.getKey(), entry.getValue());
    //            }
    //        }
    //        rocksDB.write(new WriteOptions(), batch);
    //    } catch (Exception e) {
    //        throw e;
    //    } finally {
    //        // Make sure you close the batch to avoid resource leaks.
    //        // 关闭批量操作对象释放资源
    //        if (batch != null) {
    //            batch.close();
    //        }
    //    }
    //}

    @Override
    public void updateBatch(Map<byte[], byte[]> rows) {
        //long startTime = System.nanoTime();
        resetDbLock.writeLock().lock();
        try {
            //if (logger.isTraceEnabled()) {
            //    logger.trace("~> RocksDbDataSource.updateBatch(): " + name + ", " + rows.size());
            //}
            try {
                updateBatchInternal(rows);
                //if (Log.isInfoEnabled()) {
                //    Log.info("<~ RocksDbDataSource.updateBatch(): " + name + ", " + rows.size() + ", cost {}", System.nanoTime() - startTime);
                //}
            } catch (Exception e) {
                logger.error("Error, retrying one more time...", e);
                // try one more time
                try {
                    updateBatchInternal(rows);
                    //if (logger.isTraceEnabled()) {
                    //    logger.trace("<~ RocksDbDataSource.updateBatch(): " + name + ", " + rows.size());
                    //}
                } catch (Exception e1) {
                    logger.error("Error", e);
                    throw new RuntimeException(e);
                }
            }
        } finally {
            resetDbLock.writeLock().unlock();
        }
    }

    @Override
    public boolean flush() {
        return false;
    }

    @Override
    public void close() {
    }

}
