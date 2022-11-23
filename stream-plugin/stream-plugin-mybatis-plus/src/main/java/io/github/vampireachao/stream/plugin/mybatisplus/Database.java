package io.github.vampireachao.stream.plugin.mybatisplus;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.interfaces.Join;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.*;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.SimpleQuery;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.github.vampireachao.stream.core.lambda.LambdaHelper;
import io.github.vampireachao.stream.core.lambda.function.SerBiCons;
import io.github.vampireachao.stream.core.lambda.function.SerCons;
import io.github.vampireachao.stream.core.lambda.function.SerFunc;
import io.github.vampireachao.stream.core.optional.Opp;
import io.github.vampireachao.stream.core.optional.Sf;
import io.github.vampireachao.stream.core.reflect.ReflectHelper;
import io.github.vampireachao.stream.core.stream.Steam;
import io.github.vampireachao.stream.plugin.mybatisplus.engine.constant.PluginConst;
import io.github.vampireachao.stream.plugin.mybatisplus.engine.mapper.IMapper;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.SqlSession;
import org.mybatis.spring.SqlSessionUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 辅助类
 *
 * @author VampireAchao Cizai_
 * @since 1.0
 */
public class Database {
    private static final Log log = LogFactory.getLog(Database.class);

    private static final Map<Class<?>, Map<String, String>> TABLE_PROPERTY_COLUMN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, String>> TABLE_COLUMN_PROPERTY_CACHE = new ConcurrentHashMap<>();

    /**
     *
     */
    private Database() {
        /* Do not new me! */
    }

    /**
     * @param wrapper
     * @return boolean
     */
    public static boolean isActive(AbstractWrapper<?, ?, ?> wrapper) {
        return (Objects.nonNull(wrapper)) &&
                (wrapper.getSqlComment() == null || !wrapper.getSqlComment().contains(PluginConst.WRAPPER_NOT_ACTIVE));
    }

    /**
     * @param wrapper
     * @return boolean
     */
    public static boolean isNotActive(AbstractWrapper<?, ?, ?> wrapper) {
        return !isActive(wrapper);
    }

    /**
     * @param wrapper
     * @param mapper
     * @param other
     * @return {@link R}
     */
    public static <W extends AbstractWrapper<T, ?, ?>, T, U extends R, R>
    R activeOrElse(W wrapper, Function<? super W, U> mapper, U other) {
        return isActive(wrapper) ? mapper.apply(wrapper) : other;
    }

    /**
     * @param wrapper
     * @return {@link T}
     */
    public static <T extends Join<?>> T notActive(T wrapper) {
        return notActive(true, wrapper);
    }

    /**
     * @param condition
     * @param wrapper
     * @return {@link T}
     */
    public static <T extends Join<?>> T notActive(Boolean condition, T wrapper) {
        wrapper.comment(Boolean.TRUE.equals(condition), PluginConst.WRAPPER_NOT_ACTIVE);
        return wrapper;
    }

    /**
     * @param data
     * @param condition
     * @return {@link Opp}<{@link LambdaQueryWrapper}<{@link T}>>
     */
    public static <T, E extends Serializable> Opp<LambdaQueryWrapper<T>> lambdaQuery(E data, SFunction<T, E> condition) {
        return Opp.of(data).map(value -> Wrappers.lambdaQuery(ClassUtils.newInstance(SimpleQuery.getType(condition)))
                .eq(condition, value)).filter(Database::isActive);
    }

    /**
     * @param dataList
     * @param condition
     * @return {@link Opp}<{@link LambdaQueryWrapper}<{@link T}>>
     */
    public static <T, E extends Serializable>
    Opp<LambdaQueryWrapper<T>> lambdaQuery(Collection<E> dataList, SFunction<T, E> condition) {
        return Opp.ofColl(dataList)
                .map(value -> Wrappers.lambdaQuery(ClassUtils.newInstance(SimpleQuery.getType(condition)))
                        .in(condition, new HashSet<>(value))).filter(Database::isActive);
    }

    /**
     * @param keyFunction
     * @return {@link LambdaQueryWrapper}<{@link T}>
     */
    public static <T, K> LambdaQueryWrapper<T> lambdaQuery(SFunction<T, K> keyFunction) {
        return Wrappers.lambdaQuery(ClassUtils.newInstance(SimpleQuery.getType(keyFunction)));
    }

    /**
     * @param wrapper
     * @param columns
     * @return {@link LambdaQueryWrapper}<{@link T}>
     */
    @SafeVarargs
    public static <T> LambdaQueryWrapper<T> select(LambdaQueryWrapper<T> wrapper, SFunction<T, ?>... columns) {
        return select(wrapper, LambdaQueryWrapper::select, columns);
    }

    /**
     * @param wrapper
     * @param whenAllMatchColumn
     * @param columns
     * @return {@link LambdaQueryWrapper}<{@link T}>
     */
    @SafeVarargs
    public static <T>
    LambdaQueryWrapper<T> select(LambdaQueryWrapper<T> wrapper,
                                 SerBiCons<LambdaQueryWrapper<T>, SFunction<T, ?>[]> whenAllMatchColumn,
                                 SFunction<T, ?>... columns) {
        if (Stream.of(columns).allMatch(func -> Objects.nonNull(func) &&
                PropertyNamer.isGetter(LambdaHelper.resolve(func).getLambda().getImplMethodName()))) {
            whenAllMatchColumn.accept(wrapper, columns);
        }
        return wrapper;
    }

    /**
     * 插入一条记录（选择字段，策略插入）
     *
     * @param entity 实体对象
     * @return boolean
     */
    public static <T> boolean save(T entity) {
        if (Objects.isNull(entity)) {
            return false;
        }
        Class<T> entityClass = SerFunc.<Class<?>, Class<T>>castingIdentity().apply(entity.getClass());
        Integer result = execute(entityClass, baseMapper -> baseMapper.insert(entity));
        return SqlHelper.retBool(result);
    }

    /**
     * 插入（批量）
     *
     * @param entityList 实体对象集合
     * @return boolean
     */
    public static <T> boolean saveBatch(Collection<T> entityList) {
        return saveBatch(entityList, PluginConst.DEFAULT_BATCH_SIZE);
    }

    /**
     * 插入（批量）
     *
     * @param entityList 实体对象集合
     * @param batchSize  插入批次数量
     * @return boolean
     */
    public static <T> boolean saveBatch(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList) || batchSize <= 0) {
            return false;
        }
        Class<T> entityClass = getEntityClass(entityList);
        Class<?> mapperClass = ClassUtils.toClassConfident(getTableInfo(entityClass).getCurrentNamespace());
        String sqlStatement = SqlHelper.getSqlStatement(mapperClass, SqlMethod.INSERT_ONE);
        return SqlHelper.executeBatch(entityClass, log, entityList, batchSize,
                (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
    }

    /**
     * 以几条sql方式插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @return 成功与否
     */
    public static <T> boolean saveFewSql(Collection<T> entityList) {
        return saveFewSql(entityList, PluginConst.DEFAULT_BATCH_SIZE);
    }

    /**
     * 以几条sql方式修改插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @param batchSize  分批条数
     * @return 成功与否
     */
    public static <T> boolean saveOrUpdateFewSql(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList) || batchSize < 0) {
            return false;
        }
        Class<T> entityClass = getEntityClass(entityList);
        TableInfo tableInfo = getTableInfo(entityClass);
        Map<Boolean, List<T>> isInsertDataListMap = Steam.of(entityList)
                .partition(entity -> Objects.isNull(tableInfo.getPropertyValue(entity, tableInfo.getKeyProperty())));
        return saveFewSql(isInsertDataListMap.get(true)) && updateFewSql(isInsertDataListMap.get(false));
    }

    /**
     * 以几条sql方式修改插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @return 成功与否
     */
    public static <T> boolean saveOrUpdateFewSql(Collection<T> entityList) {
        return saveOrUpdateFewSql(entityList, PluginConst.DEFAULT_BATCH_SIZE);
    }

    /**
     * 以几条sql方式插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @param batchSize  分批条数
     * @return 成功与否
     */
    public static <T> boolean saveFewSql(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList) || batchSize <= 0) {
            return false;
        }
        return execute(getEntityClass(entityList),
                (IMapper<T> baseMapper) -> entityList.size() == baseMapper.saveFewSql(entityList, batchSize));
    }


    /**
     * 以单条sql方式插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @return 成功与否
     */
    public static <T> boolean updateOneSql(Collection<T> entityList) {
        if (CollectionUtils.isEmpty(entityList)) {
            return false;
        }
        return execute(getEntityClass(entityList),
                (IMapper<T> baseMapper) -> entityList.size() == baseMapper.updateOneSql(entityList));
    }

    /**
     * 以几条sql方式插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @return 成功与否
     */
    public static <T> boolean updateFewSql(Collection<T> entityList) {
        return updateFewSql(entityList, PluginConst.DEFAULT_BATCH_SIZE);
    }

    /**
     * 以几条sql方式插入（批量）需要实现IMapper
     *
     * @param entityList 数据
     * @param batchSize  分批条数
     * @return 成功与否
     */
    public static <T> boolean updateFewSql(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList) || batchSize <= 0) {
            return false;
        }
        return execute(getEntityClass(entityList),
                (IMapper<T> baseMapper) -> entityList.size() == baseMapper.updateFewSql(entityList, batchSize));
    }

    /**
     * 批量修改插入
     *
     * @param entityList 实体对象集合
     * @return boolean
     */
    public static <T> boolean saveOrUpdateBatch(Collection<T> entityList) {
        return saveOrUpdateBatch(entityList, PluginConst.DEFAULT_BATCH_SIZE);
    }

    /**
     * 批量修改插入
     *
     * @param entityList 实体对象集合
     * @param batchSize  每次的数量
     * @return boolean
     */
    public static <T> boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList) || batchSize < 0) {
            return false;
        }
        Class<T> entityClass = getEntityClass(entityList);
        TableInfo tableInfo = getTableInfo(entityClass);
        Class<?> mapperClass = ClassUtils.toClassConfident(tableInfo.getCurrentNamespace());
        String keyProperty = tableInfo.getKeyProperty();
        Assert.notEmpty(keyProperty, "error: can not execute. because can not find column for primary key from entity!");
        return SqlHelper.saveOrUpdateBatch(entityClass, mapperClass, log, entityList, batchSize,
                (sqlSession, entity) -> {
                    Object idVal = tableInfo.getPropertyValue(entity, keyProperty);
                    return StringUtils.checkValNull(idVal)
                            || CollectionUtils.isEmpty(sqlSession.selectList(SqlHelper.getSqlStatement(mapperClass,
                            SqlMethod.SELECT_BY_ID), entity));
                }, (sqlSession, entity) -> {
                    MapperMethod.ParamMap<T> param = new MapperMethod.ParamMap<>();
                    param.put(Constants.ENTITY, entity);
                    sqlSession.update(SqlHelper.getSqlStatement(mapperClass, SqlMethod.UPDATE_BY_ID), param);
                });
    }

    /**
     * 根据 ID 删除
     *
     * @param id          主键ID
     * @param entityClass 实体类
     * @return boolean
     */
    public static <T> boolean removeById(Serializable id, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> SqlHelper.retBool(baseMapper.deleteById(id)));
    }

    /**
     * 根据实体(ID)删除
     *
     * @param entity 实体
     * @return boolean
     */
    public static <T> boolean removeById(T entity) {
        if (Objects.isNull(entity)) {
            return false;
        }
        Class<T> entityClass = SerFunc.<Class<?>, Class<T>>castingIdentity().apply(entity.getClass());
        return execute(entityClass, baseMapper -> SqlHelper.retBool(baseMapper.deleteById(entity)));
    }

    /**
     * 根据 entity 条件，删除记录
     *
     * @param queryWrapper 实体包装类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return boolean
     */
    public static <T> boolean remove(AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, w -> SqlHelper.retBool(baseMapper.delete(w)), false));
    }

    /**
     * 根据 ID 选择修改
     *
     * @param entity 实体对象
     * @return boolean
     */
    public static <T> boolean updateById(T entity) {
        if (Objects.isNull(entity)) {
            return false;
        }
        Class<T> entityClass = SerFunc.<Class<?>, Class<T>>castingIdentity().apply(entity.getClass());
        return execute(entityClass, baseMapper -> SqlHelper.retBool(baseMapper.updateById(entity)));
    }

    /**
     * 强制根据id修改，指定的字段不管是否为null也会修改
     *
     * @param entity     实体对象
     * @param updateKeys 指定字段
     * @return 是否成功
     */
    @SafeVarargs
    public static <T> boolean updateForceById(T entity, SFunction<T, ?>... updateKeys) {
        if (Objects.isNull(entity) || ArrayUtils.isEmpty(updateKeys)) {
            return updateById(entity);
        }
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        TableInfo tableInfo = getTableInfo(entityClass);
        T bean = ClassUtils.newInstance(entityClass);
        String keyProperty = tableInfo.getKeyProperty();
        ReflectHelper.setFieldValue(bean, keyProperty, ReflectionKit.getFieldValue(entity, keyProperty));
        LambdaUpdateWrapper<T> updateWrapper = Stream.of(updateKeys).reduce(Wrappers.lambdaUpdate(bean),
                (wrapper, field) -> wrapper.set(field, field.apply(entity)), (l, r) -> r);
        return update(bean, updateWrapper);
    }

    /**
     * 根据 UpdateWrapper 条件，更新记录 需要设置sqlset
     *
     * @param updateWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper}
     * @return boolean
     */
    public static <T> boolean update(AbstractWrapper<T, ?, ?> updateWrapper) {
        return update(null, updateWrapper);
    }

    /**
     * 根据 whereEntity 条件，更新记录
     *
     * @param entity        实体对象
     * @param updateWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper}
     * @return boolean
     */
    public static <T> boolean update(T entity, AbstractWrapper<T, ?, ?> updateWrapper) {
        return execute(getEntityClass(updateWrapper),
                baseMapper -> activeOrElse(updateWrapper, w -> SqlHelper.retBool(baseMapper.update(entity, w)),
                        false));
    }

    /**
     * 根据ID 批量修改
     *
     * @param entityList 实体对象集合
     * @return boolean
     */
    public static <T> boolean updateBatchById(Collection<T> entityList) {
        return updateBatchById(entityList, PluginConst.DEFAULT_BATCH_SIZE);
    }

    /**
     * 根据ID 批量修改
     *
     * @param entityList 实体对象集合
     * @param batchSize  修改批次数量
     * @return boolean
     */
    public static <T> boolean updateBatchById(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList) || batchSize <= 0) {
            return false;
        }
        Class<T> entityClass = getEntityClass(entityList);
        TableInfo tableInfo = getTableInfo(entityClass);
        String sqlStatement = SqlHelper.getSqlStatement(ClassUtils.toClassConfident(tableInfo.getCurrentNamespace()),
                SqlMethod.UPDATE_BY_ID);
        return SqlHelper.executeBatch(entityClass, log, entityList, batchSize, (sqlSession, entity) -> {
            MapperMethod.ParamMap<T> param = new MapperMethod.ParamMap<>();
            param.put(Constants.ENTITY, entity);
            sqlSession.update(sqlStatement, param);
        });
    }

    /**
     * 删除（根据ID 批量删除）
     *
     * @param list        主键ID或实体列表
     * @param entityClass 实体类
     * @return boolean
     */
    public static <T> boolean removeByIds(Collection<? extends Serializable> list, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> SqlHelper.retBool(baseMapper.deleteBatchIds(list)));
    }

    /**
     * 根据 columnMap 条件，删除记录
     *
     * @param columnMap   表字段 map 对象
     * @param entityClass 实体类
     * @return boolean
     */
    public static <T> boolean removeByMap(Map<String, Object> columnMap, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> SqlHelper.retBool(baseMapper.deleteByMap(columnMap)));
    }

    /**
     * TableId 注解存在修改记录，否插入一条记录
     *
     * @param entity 实体对象
     * @return boolean
     */
    public static <T> boolean saveOrUpdate(T entity) {
        if (Objects.isNull(entity)) {
            return false;
        }
        Class<T> entityClass = SerFunc.<Class<?>, Class<T>>castingIdentity().apply(entity.getClass());
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        Assert.notNull(tableInfo, "error: can not execute. because can not find cache of TableInfo for entity!");
        String keyProperty = tableInfo.getKeyProperty();
        Assert.notEmpty(keyProperty, "error: can not execute. because can not find column for id from entity!");
        Object idVal = tableInfo.getPropertyValue(entity, tableInfo.getKeyProperty());
        return StringUtils.checkValNull(idVal) || Objects.isNull(getById((Serializable) idVal, entityClass)) ?
                save(entity) : updateById(entity);
    }

    /**
     * 根据 ID 查询
     *
     * @param id          主键ID
     * @param entityClass 实体类
     * @return {@link T}
     */
    public static <T> T getById(Serializable id, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectById(id));
    }

    /**
     * 根据 Wrapper，查询一条记录 <br/>
     * <p>结果集，如果是多个会抛出异常，随机取一条加上限制条件 wrapper.last("LIMIT 1")</p>
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link T}
     */
    public static <T> T getOne(AbstractWrapper<T, ?, ?> queryWrapper) {
        return getOne(queryWrapper, true);
    }

    /**
     * 根据 Wrapper，查询一条记录
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @param throwEx      有多个 result 是否抛出异常
     * @return {@link T}
     */
    public static <T> T getOne(AbstractWrapper<T, ?, ?> queryWrapper, boolean throwEx) {
        if (!isActive(queryWrapper)) {
            return null;
        }
        Class<T> entityClass = getEntityClass(queryWrapper);
        if (throwEx) {
            return execute(entityClass, baseMapper -> baseMapper.selectOne(queryWrapper));
        }
        return execute(entityClass, baseMapper -> SqlHelper.getObject(log, baseMapper.selectList(queryWrapper)));
    }

    /**
     * 查询（根据 columnMap 条件）
     *
     * @param columnMap   表字段 map 对象
     * @param entityClass 实体类
     * @return {@link List}<{@link T}>
     */
    public static <T> List<T> listByMap(Map<String, Object> columnMap, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectByMap(columnMap));
    }

    /**
     * 查询（根据ID 批量查询）
     *
     * @param idList      主键ID列表
     * @param entityClass 实体类
     * @return {@link List}<{@link T}>
     */
    public static <T> List<T> listByIds(Collection<? extends Serializable> idList, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectBatchIds(idList));
    }

    /**
     * 根据 Wrapper，查询一条记录
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link Map}<{@link String}, {@link Object}>
     */
    public static <T> Map<String, Object> getMap(AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper), baseMapper -> activeOrElse(queryWrapper,
                w -> SqlHelper.getObject(log, baseMapper.selectMaps(w)), null));
    }

    /**
     * 查询总记录数
     *
     * @param entityClass 实体类
     * @return long
     * @see Wrappers#emptyWrapper()
     */
    public static <T> long count(Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectCount(null));
    }

    /**
     * 根据 Wrapper 条件，查询总记录数
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return long
     */
    public static <T> long count(AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, baseMapper::selectCount, 0L));
    }

    /**
     * 查询列表
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link List}<{@link T}>
     */
    public static <T> List<T> list(AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, baseMapper::selectList, new ArrayList<>()));
    }

    /**
     * 查询所有
     *
     * @param entityClass 实体类
     * @return {@link List}<{@link T}>
     * @see Wrappers#emptyWrapper()
     */
    public static <T> List<T> list(Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectList(null));
    }

    /**
     * 查询列表
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link List}<{@link Map}<{@link String}, {@link Object}>>
     */
    public static <T> List<Map<String, Object>> listMaps(AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, baseMapper::selectMaps, new ArrayList<>()));
    }

    /**
     * 查询所有列表
     *
     * @param entityClass 实体类
     * @return {@link List}<{@link Map}<{@link String}, {@link Object}>>
     * @see Wrappers#emptyWrapper()
     */
    public static <T> List<Map<String, Object>> listMaps(Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectMaps(null));
    }

    /**
     * 查询全部记录
     *
     * @param entityClass 实体类
     * @return {@link List}<{@link T}>
     */
    public static <T> List<T> listObjs(Class<T> entityClass) {
        return listObjs(entityClass, i -> i);
    }

    /**
     * 根据 Wrapper 条件，查询全部记录
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link List}<{@link Object}>
     */
    public static <T> List<Object> listObjs(AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, baseMapper::selectObjs, new ArrayList<>()));
    }

    /**
     * 根据 Wrapper 条件，查询全部记录
     *
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @param mapper       转换函数
     * @return {@link List}<{@link V}>
     */
    public static <T, V> List<V> listObjs(AbstractWrapper<T, ?, ?> queryWrapper, SFunction<? super T, V> mapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, w -> Steam.of(baseMapper.selectList(w)).map(mapper).toList(),
                        new ArrayList<>()));
    }

    /**
     * 查询全部记录
     *
     * @param entityClass 实体类
     * @param mapper      转换函数
     * @return {@link List}<{@link V}>
     */
    public static <T, V> List<V> listObjs(Class<T> entityClass, SFunction<? super T, V> mapper) {
        return execute(entityClass, baseMapper -> Steam.of(baseMapper.selectList(null)).map(mapper).toList());
    }

    /**
     * 无条件翻页查询
     *
     * @param page        翻页对象
     * @param entityClass 实体类
     * @return {@link E}
     * @see Wrappers#emptyWrapper()
     */
    public static <T, E extends IPage<Map<String, Object>>> E pageMaps(E page, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectMapsPage(page, null));
    }

    /**
     * 翻页查询
     *
     * @param page         翻页对象
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link E}
     */
    public static <T, E extends IPage<Map<String, Object>>> E pageMaps(E page, AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, w -> baseMapper.selectMapsPage(page, w), page));
    }

    /**
     * 无条件翻页查询
     *
     * @param page        翻页对象
     * @param entityClass 实体类
     * @return {@link IPage}<{@link T}>
     * @see Wrappers#emptyWrapper()
     */
    public static <T> IPage<T> page(IPage<T> page, Class<T> entityClass) {
        return execute(entityClass, baseMapper -> baseMapper.selectPage(page, null));
    }

    /**
     * 翻页查询
     *
     * @param page         翻页对象
     * @param queryWrapper 实体对象封装操作类 {@link com.baomidou.mybatisplus.core.conditions.query.QueryWrapper}
     * @return {@link IPage}<{@link T}>
     */
    public static <T> IPage<T> page(IPage<T> page, AbstractWrapper<T, ?, ?> queryWrapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, w -> baseMapper.selectPage(page, w), page));
    }

    /**
     * <p>
     * 根据updateWrapper尝试修改，否继续执行saveOrUpdate(T)方法
     * 此次修改主要是减少了此项业务代码的代码量（存在性验证之后的saveOrUpdate操作）
     * </p>
     *
     * @param entity        实体对象
     * @param updateWrapper 更新构造器
     * @return boolean
     */
    public static <T> boolean saveOrUpdate(T entity, AbstractWrapper<T, ?, ?> updateWrapper) {
        if (!isActive(updateWrapper)) {
            return false;
        }
        return update(entity, updateWrapper) || saveOrUpdate(entity);
    }

    /**
     * 根据 Wrapper，查询一条记录
     *
     * @param queryWrapper 实体对象封装操作类
     * @param mapper       转换函数
     * @return {@link V}
     */
    public static <T, V> V getObj(AbstractWrapper<T, ?, ?> queryWrapper, SFunction<? super T, V> mapper) {
        return execute(getEntityClass(queryWrapper),
                baseMapper -> activeOrElse(queryWrapper, w -> mapper.apply(baseMapper.selectOne(w)), null));
    }

    /**
     * 通过entityClass获取BaseMapper，再传入lambda使用该mapper，本方法自动释放链接
     *
     * @param entityClass 实体类
     * @param sFunction   lambda操作
     * @return {@link BaseMapper} 返回lambda执行结果
     */
    @SuppressWarnings("unchecked")
    public static <T, R, M extends BaseMapper<T>> R execute(Class<T> entityClass, SFunction<M, R> sFunction) {
        SqlSession sqlSession = SqlHelper.sqlSession(entityClass);
        try {
            return sFunction.apply((M) SqlHelper.getMapper(entityClass, sqlSession));
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, GlobalConfigUtils.currentSessionFactory(entityClass));
        }
    }

    /**
     * 获取po对应的 {@code Map<属性,字段>}
     *
     * @param entityClass 实体类型
     * @return {@link Map}<{@link String}, {@link String}>
     */
    public static Map<String, String> getPropertyColumnMap(Class<?> entityClass) {
        return TABLE_PROPERTY_COLUMN_CACHE.computeIfAbsent(entityClass,
                clazz -> {
                    TableInfo tableInfo = getTableInfo(clazz);
                    Map<String, String> propertyColumnMap = Steam.of(tableInfo.getFieldList())
                            .toMap(TableFieldInfo::getProperty, TableFieldInfo::getColumn);
                    propertyColumnMap.put(tableInfo.getKeyProperty(), tableInfo.getKeyColumn());
                    return Collections.unmodifiableMap(propertyColumnMap);
                });
    }

    /**
     * 获取po对应的 {@code Map<字段,属性>}
     *
     * @param entityClass 实体类型
     * @return {@link Map}<{@link String}, {@link String}>
     */
    public static Map<String, String> getColumnPropertyMap(Class<?> entityClass) {
        return TABLE_COLUMN_PROPERTY_CACHE.computeIfAbsent(entityClass,
                clazz -> {
                    TableInfo tableInfo = getTableInfo(clazz);
                    Map<String, String> columnPropertyMap = Steam.of(tableInfo.getFieldList())
                            .toMap(TableFieldInfo::getColumn, TableFieldInfo::getProperty);
                    columnPropertyMap.put(tableInfo.getKeyColumn(), tableInfo.getKeyProperty());
                    return Collections.unmodifiableMap(columnPropertyMap);
                });
    }

    /**
     * 通过属性lambda获取字段名
     *
     * @param property 属性lambda
     * @return 字段名
     */
    public static <T, R extends Comparable<R>> String propertyToColumn(SFunction<T, R> property) {
        LambdaMeta lambdaMeta = LambdaUtils.extract(property);
        return propertyToColumn(lambdaMeta.getInstantiatedClass(),
                PropertyNamer.methodToProperty(lambdaMeta.getImplMethodName()));
    }

    /**
     * 通过属性名获取字段名
     *
     * @param clazz    实体类型
     * @param property 属性名
     * @return 字段名
     */
    public static String propertyToColumn(Class<?> clazz, String property) {
        return getPropertyColumnMap(clazz).get(property);
    }

    /**
     * 通过字段名获取属性名
     *
     * @param clazz  实体类型
     * @param column 字段名
     * @return 属性名
     */
    public static String columnToProperty(Class<?> clazz, String column) {
        return getColumnPropertyMap(clazz).get(column);
    }

    /**
     * 将orders里的column从property转column
     *
     * @param page  page对象
     * @param clazz 实体类型
     */
    @SuppressWarnings("deprecation")
    public static <T> void ordersPropertyToColumn(Page<T> page, Class<T> clazz) {
        page.getOrders().forEach(SerCons.multi(
                order -> Sf.of(order.getColumn()).takeUnless(SqlInjectionUtils::check)
                        .require(() -> new IllegalArgumentException(
                                String.format("order column { %s } must not null or be sql injection",
                                        order.getColumn()))),
                order -> order.setColumn(propertyToColumn(clazz, order.getColumn()))
        ));
    }

    /**
     * 从集合中获取实体类型
     *
     * @param entityList 实体集合
     * @return 实体类型
     */
    private static <T> Class<T> getEntityClass(Collection<T> entityList) {
        Class<T> entityClass = null;
        for (T entity : entityList) {
            if (entity != null && entity.getClass() != null) {
                entityClass = SerFunc.<Class<?>, Class<T>>castingIdentity().apply(entity.getClass());
                break;
            }
        }
        Assert.notNull(entityClass, "error: can not get entityClass from entityList");
        return entityClass;
    }

    /**
     * 从wrapper中尝试获取实体类型
     *
     * @param queryWrapper 条件构造器
     * @return 实体类型
     */
    private static <T> Class<T> getEntityClass(AbstractWrapper<T, ?, ?> queryWrapper) {
        Class<T> entityClass = queryWrapper.getEntityClass();
        if (entityClass == null) {
            T entity = queryWrapper.getEntity();
            if (entity != null) {
                entityClass = SerFunc.<Class<?>, Class<T>>castingIdentity().apply(entity.getClass());
            }
        }
        Assert.notNull(entityClass, "error: can not get entityClass from wrapper");
        return entityClass;
    }

    /**
     * 获取表信息，获取不到报错提示
     *
     * @param entityClass 实体类
     * @return 对应表信息
     */
    private static <T> TableInfo getTableInfo(Class<T> entityClass) {
        return Optional.ofNullable(TableInfoHelper.getTableInfo(entityClass))
                .orElseThrow(() -> ExceptionUtils.mpe("error: can not find TableInfo from Class: \"%s\".",
                        entityClass.getName()));
    }

}
