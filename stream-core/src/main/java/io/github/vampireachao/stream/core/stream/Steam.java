package io.github.vampireachao.stream.core.stream;

import io.github.vampireachao.stream.core.collector.Collective;
import io.github.vampireachao.stream.core.optional.Opp;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.*;

/**
 * 对Stream的封装和拓展，作者经对比了vavr、eclipse-collection、stream-ex以及其他语言的api，结合日常使用习惯，进行封装和拓展
 * Stream为集合提供了一些易用api，它让开发人员能使用声明式编程的方式去编写代码
 * 它分为中间操作和结束操作
 * 中间操作分为
 * <ul>
 *     <li>
 * 			无状态中间操作: 表示不用等待 所有元素的当前操作执行完 就可以执行的操作，不依赖之前历史操作的流的状态
 *     </li>
 *     <li>
 *         有状态中间操作: 表示需要等待 所有元素的当前操作执行完 才能执行的操作,依赖之前历史操作的流的状态
 *     </li>
 * </ul>
 * 结束操作分为
 * <ul>
 *     <li>
 *   	短路结束操作: 表示不用等待 所有元素的当前操作执行完 就可以执行的操作
 *     </li>
 *     <li>
 *       非短路结束操作: 表示需要等待 所有元素的当前操作执行完 才能执行的操作
 *     </li>
 * </ul>
 * 流只有在 结束操作 时才会真正触发执行以往的 中间操作
 * <p>
 * 它分为串行流和并行流
 * 并行流会使用拆分器{@link Spliterator}将操作拆分为多个异步任务{@link java.util.concurrent.ForkJoinTask}执行
 * 这些异步任务默认使用{@link java.util.concurrent.ForkJoinPool}线程池进行管理
 *
 * @author VampireAchao
 * @see java.util.stream.Stream
 */
public class Steam<T> implements Stream<T>, Iterable<T> {
    /**
     * 代表不存在的下标, 一般用于并行流的下标, 或者未找到元素时的下标
     */
    private static final int NOT_FOUND_INDEX = -1;

    protected final Stream<T> stream;

    Steam(Stream<T> stream) {
        this.stream = stream;
    }

    // --------------------------------------------------------------- Static method start

    /**
     * 返回{@code Steam}的建造器
     *
     * @param <T> 元素的类型
     * @return a stream builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<T>() {
            private final Stream.Builder<T> builder = Stream.builder();

            @Override
            public void accept(T t) {
                builder.accept(t);
            }

            @Override
            public Steam<T> build() {
                return new Steam<>(builder.build());
            }
        };
    }

    /**
     * 返回空的串行流
     *
     * @param <T> 元素类型
     * @return 一个空的串行流
     */
    public static <T> Steam<T> empty() {
        return new Steam<>(Stream.empty());
    }

    /**
     * 返回包含单个元素的串行流
     *
     * @param t   单个元素
     * @param <T> 元素类型
     * @return 包含单个元素的串行流
     */
    public static <T> Steam<T> of(T t) {
        return new Steam<>(Stream.of(t));
    }

    /**
     * 返回包含指定元素的串行流
     *
     * @param values 指定元素
     * @param <T>    元素类型
     * @return 包含指定元素的串行流
     * 从一个安全数组中创建流
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Steam<T> of(T... values) {
        return (values == null || values.length == 0) ? empty() : new Steam<>(Stream.of(values));
    }

    /**
     * 返回无限有序流
     * 该流由 初始值 以及执行 迭代函数 进行迭代获取到元素
     * <p>
     * 例如
     * {@code Steam.iterate(0, i -> i + 1)}
     * 就可以创建从0开始，每次+1的无限流，使用{@link Steam#limit(long)}可以限制元素个数
     * </p>
     *
     * @param <T>  元素类型
     * @param seed 初始值
     * @param f    用上一个元素作为参数执行并返回一个新的元素
     * @return 无限有序流
     */
    public static <T> Steam<T> iterate(final T seed, final UnaryOperator<T> f) {
        return new Steam<>(Stream.iterate(seed, f));
    }

    /**
     * 返回无限有序流
     * 该流由 初始值 然后判断条件 以及执行 迭代函数 进行迭代获取到元素
     * <p>
     * 例如
     * {@code Steam.iterate(0, i -> i < 3, i -> ++i)}
     * 就可以创建包含元素0,1,2的流，使用{@link Steam#limit(long)}可以限制元素个数
     * </p>
     *
     * @param <T>     元素类型
     * @param seed    初始值
     * @param hasNext 条件值
     * @param next    用上一个元素作为参数执行并返回一个新的元素
     * @return 无限有序流
     */
    public static <T> Steam<T> iterate(T seed, Predicate<? super T> hasNext, UnaryOperator<T> next) {
        Objects.requireNonNull(next);
        Objects.requireNonNull(hasNext);
        Spliterator<T> spliterator = new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.IMMUTABLE) {
            T prev;
            boolean started;
            boolean finished;

            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                Objects.requireNonNull(action);
                if (finished) {
                    return false;
                }
                T t;
                if (started) {
                    t = next.apply(prev);
                } else {
                    t = seed;
                    started = true;
                }
                if (!hasNext.test(t)) {
                    prev = null;
                    finished = true;
                    return false;
                }
                prev = t;
                action.accept(prev);
                return true;
            }

            @Override
            public void forEachRemaining(Consumer<? super T> action) {
                Objects.requireNonNull(action);
                if (finished) {
                    return;
                }
                finished = true;
                T t = started ? next.apply(prev) : seed;
                prev = null;
                while (hasNext.test(t)) {
                    action.accept(t);
                    t = next.apply(t);
                }
            }
        };
        return new Steam<>(StreamSupport.stream(spliterator, false));
    }

    /**
     * 返回无限串行无序流
     * 其中每一个元素都由给定的{@code Supplier}生成
     * 适用场景在一些生成常量流、随机元素等
     *
     * @param <T> 元素类型
     * @param s   用来生成元素的 {@code Supplier}
     * @return 无限串行无序流
     */
    public static <T> Steam<T> generate(Supplier<T> s) {
        return new Steam<>(Stream.generate(s));
    }

    /**
     * 创建一个惰性拼接流，其元素是第一个流的所有元素，然后是第二个流的所有元素。
     * 如果两个输入流都是有序的，则结果流是有序的，如果任一输入流是并行的，则结果流是并行的。
     * 当结果流关闭时，两个输入流的关闭处理程序都会被调用。
     *
     * <p>从重复串行流进行拼接时可能会导致深度调用链甚至抛出 {@code StackOverflowException}</p>
     *
     * @param <T> 元素类型
     * @param a   第一个流
     * @param b   第二个流
     * @return 拼接两个流之后的流
     */
    public static <T> Steam<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
        return new Steam<>(Stream.concat(a, b));
    }

    /**
     * 通过实现了{@link Iterable}接口的对象创建串行流
     *
     * @param iterable 实现了{@link Iterable}接口的对象
     * @param <T>      元素类型
     * @return 流
     */
    public static <T> Steam<T> of(Iterable<T> iterable) {
        return of(iterable, false);
    }

    /**
     * 通过传入的{@link Iterable}创建流
     *
     * @param iterable {@link Iterable}
     * @param parallel 是否并行
     * @param <T>      元素类型
     * @return 流
     */
    public static <T> Steam<T> of(Iterable<T> iterable, boolean parallel) {
        return Opp.of(iterable).map(Iterable::spliterator).map(spliterator -> StreamSupport.stream(spliterator, parallel)).map(Steam::new).orElseGet(Steam::empty);
    }

    /**
     * 通过传入的{@link Stream}创建流
     *
     * @param stream {@link Stream}
     * @param <T>    元素类型
     * @return 流
     */
    public static <T> Steam<T> of(Stream<T> stream) {
        return new Steam<>(Objects.requireNonNull(stream));
    }

    /**
     * 拆分字符串，转换为串行流
     *
     * @param str   字符串
     * @param regex 正则
     * @return 拆分后元素组成的流
     */
    public static Steam<String> split(CharSequence str, String regex) {
        return Opp.blank(str).map(CharSequence::toString).map(s -> s.split(regex)).map(Steam::of).orElseGet(Steam::empty);
    }

    // --------------------------------------------------------------- Static method end

    /**
     * 过滤元素，返回与指定断言匹配的元素组成的流
     * 这是一个无状态中间操作
     *
     * @param predicate 断言
     * @return 返回叠加过滤操作后的流
     */
    @Override
    public Steam<T> filter(Predicate<? super T> predicate) {
        return new Steam<>(stream.filter(predicate));
    }

    /**
     * 过滤元素，返回与 指定操作结果 匹配 指定值 的元素组成的流
     * 这是一个无状态中间操作
     *
     * @param <R>    返回类型
     * @param mapper 操作
     * @param value  用来匹配的值
     * @return 与 指定操作结果 匹配 指定值 的元素组成的流
     */
    public <R> Steam<T> filter(Function<? super T, ? extends R> mapper, R value) {
        Objects.requireNonNull(mapper);
        return filter(e -> Objects.equals(Opp.of(e).map(mapper).get(), value));
    }


    /**
     * 过滤元素，返回与指定断言匹配的元素组成的流，断言带下标，并行流时下标永远为-1
     * 这是一个无状态中间操作
     *
     * @param predicate 断言
     * @return 返回叠加过滤操作后的流
     */
    public Steam<T> filterIdx(BiPredicate<? super T, Integer> predicate) {
        Objects.requireNonNull(predicate);
        if (isParallel()) {
            return filter(e -> predicate.test(e, NOT_FOUND_INDEX));
        } else {
            AtomicInteger index = new AtomicInteger(NOT_FOUND_INDEX);
            return filter(e -> predicate.test(e, index.incrementAndGet()));
        }
    }

    /**
     * 过滤掉空元素
     *
     * @return 过滤后的流
     */
    public Steam<T> nonNull() {
        return new Steam<>(stream.filter(Objects::nonNull));
    }

    /**
     * 返回与指定函数将元素作为参数执行的结果组成的流
     * 这是一个无状态中间操作
     *
     * @param mapper 指定的函数
     * @param <R>    函数执行后返回的类型
     * @return 返回叠加操作后的流
     */
    @Override
    public <R> Steam<R> map(Function<? super T, ? extends R> mapper) {
        return new Steam<>(stream.map(mapper));
    }

    /**
     * 返回与指定函数将元素作为参数执行的结果组成的流，操作带下标，并行流时下标永远为-1
     * 这是一个无状态中间操作
     *
     * @param mapper 指定的函数
     * @param <R>    函数执行后返回的类型
     * @return 返回叠加操作后的流
     */
    public <R> Steam<R> mapIdx(BiFunction<? super T, Integer, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        if (isParallel()) {
            return map(e -> mapper.apply(e, NOT_FOUND_INDEX));
        } else {
            AtomicInteger index = new AtomicInteger(NOT_FOUND_INDEX);
            return map(e -> mapper.apply(e, index.incrementAndGet()));
        }
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流
     * 这是一个无状态中间操作
     * 例如，将users里所有user的id和parentId组合在一起，形成一个新的流:
     * <pre>{@code
     *     Steam<Long> ids = Steam.of(users).flatMap(user -> Steam.of(user.getId(), user.getParentId()));
     * }</pre>
     *
     * @param mapper 操作，返回流
     * @param <R>    拆分后流的元素类型
     * @return 返回叠加拆分操作后的流
     */
    @Override
    public <R> Steam<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return new Steam<>(stream.flatMap(mapper));
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流，操作带下标，并行流时下标永远为-1
     * 这是一个无状态中间操作
     *
     * @param mapper 操作，返回流
     * @param <R>    拆分后流的元素类型
     * @return 返回叠加拆分操作后的流
     */
    public <R> Steam<R> flatIdx(BiFunction<? super T, Integer, ? extends Stream<? extends R>> mapper) {
        Objects.requireNonNull(mapper);
        if (isParallel()) {
            return flatMap(e -> mapper.apply(e, NOT_FOUND_INDEX));
        } else {
            AtomicInteger index = new AtomicInteger(NOT_FOUND_INDEX);
            return flatMap(e -> mapper.apply(e, index.incrementAndGet()));
        }
    }

    /**
     * 和{@link Steam#map(Function)}一样，只不过函数的返回值必须为int类型
     * 这是一个无状态中间操作
     *
     * @param mapper 返回值为int类型的函数
     * @return 叠加操作后元素类型全为int的流
     */
    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return stream.mapToInt(mapper);
    }

    /**
     * 和{@link Steam#map(Function)}一样，只不过函数的返回值必须为long类型
     * 这是一个无状态中间操作
     *
     * @param mapper 返回值为long类型的函数
     * @return 叠加操作后元素类型全为long的流
     */
    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return stream.mapToLong(mapper);
    }

    /**
     * 和{@link Steam#map(Function)}一样，只不过函数的返回值必须为double类型
     * 这是一个无状态中间操作
     *
     * @param mapper 返回值为double类型的函数
     * @return 叠加操作后元素类型全为double的流
     */
    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return stream.mapToDouble(mapper);
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流
     * 这是一个无状态中间操作
     * 例如，将users里所有user的id和parentId组合在一起，形成一个新的流:
     * <pre>{@code
     *     Steam<Long> ids = Steam.of(users).flatMap(user -> Steam.of(user.getId(), user.getParentId()));
     * }</pre>
     *
     * @param mapper 操作，返回可迭代对象
     * @param <R>    拆分后流的元素类型
     * @return 返回叠加拆分操作后的流
     */
    public <R> Steam<R> flatIter(Function<? super T, ? extends Iterable<? extends R>> mapper) {
        Objects.requireNonNull(mapper);
        return flatMap(w -> Opp.required(w).map(mapper).map(Steam::of).orElseGet(Steam::empty));
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流
     * 这是一个无状态中间操作
     *
     * @param mapper 操作，返回IntStream
     * @return 返回叠加拆分操作后的IntStream
     */
    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return stream.flatMapToInt(mapper);
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流
     * 这是一个无状态中间操作
     *
     * @param mapper 操作，返回LongStream
     * @return 返回叠加拆分操作后的LongStream
     */
    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return stream.flatMapToLong(mapper);
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流
     * 这是一个无状态中间操作
     *
     * @param mapper 操作，返回DoubleStream
     * @return 返回叠加拆分操作后的DoubleStream
     */
    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return stream.flatMapToDouble(mapper);
    }

    /**
     * 扩散流操作，可能影响流元素个数，将原有流元素执行mapper操作，返回多个流所有元素组成的流，操作带一个方法，调用该方法可增加元素
     * 这是一个无状态中间操作
     *
     * @param mapper 操作，返回流
     * @param <R>    拆分后流的元素类型
     * @return 返回叠加拆分操作后的流
     */
    public <R> Steam<R> mapMulti(BiConsumer<? super T, ? super Builder<R>> mapper) {
        Objects.requireNonNull(mapper);
        return flatMap(e -> {
            Builder<R> buffer = Steam.builder();
            mapper.accept(e, buffer);
            return buffer.build();
        });
    }

    /**
     * 返回一个具有去重特征的流 非并行流(顺序流)下对于重复元素，保留遇到顺序中最先出现的元素，并行流情况下不能保证具体保留哪一个
     * 这是一个有状态中间操作
     *
     * @return 一个具有去重特征的流
     */
    @Override
    public Steam<T> distinct() {
        return new Steam<>(stream.distinct());
    }

    /**
     * 返回一个具有去重特征的流 非并行流(顺序流)下对于重复元素，保留遇到顺序中最先出现的元素，并行流情况下不能保证具体保留哪一个
     * 这是一个有状态中间操作
     *
     * @param keyExtractor 去重依据
     * @return 一个具有去重特征的流
     */
    public <F> Steam<T> distinct(Function<? super T, F> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        if (isParallel()) {
            ConcurrentHashMap<F, Boolean> exists = new ConcurrentHashMap<>(32);
            // 标记是否出现过null值，用于保留第一个出现的null
            // 由于ConcurrentHashMap的key不能为null，所以用此变量来标记
            AtomicBoolean hasNull = new AtomicBoolean(false);
            return of(stream.filter(e -> {
                F key = keyExtractor.apply(e);
                if (key == null) {
                    // 已经出现过null值，跳过该值
                    if (hasNull.get()) {
                        return false;
                    }
                    hasNull.set(Boolean.TRUE);
                    return true;
                } else {
                    // 第一次出现的key返回true
                    return null == exists.putIfAbsent(key, Boolean.TRUE);
                }
            })).parallel();
        } else {
            Set<F> exists = new HashSet<>();
            return of(stream.filter(e -> exists.add(keyExtractor.apply(e))));
        }
    }

    /**
     * 返回一个元素按自然顺序排序的流
     * 如果此流的元素不是{@code Comparable} ，则在执行终端操作时可能会抛出 {@code java.lang.ClassCastException}
     * 对于顺序流，排序是稳定的。 对于无序流，没有稳定性保证。
     * 这是一个有状态中间操作
     *
     * @return 一个元素按自然顺序排序的流
     */
    @Override
    public Steam<T> sorted() {
        return new Steam<>(stream.sorted());
    }

    /**
     * 返回一个元素按指定的{@link Comparator}排序的流
     * 如果此流的元素不是{@code Comparable} ，则在执行终端操作时可能会抛出{@code java.lang.ClassCastException}
     * 对于顺序流，排序是稳定的。 对于无序流，没有稳定性保证。
     * 这是一个有状态中间操作
     *
     * @param comparator 排序规则
     * @return 一个元素按指定的Comparator排序的流
     */
    @Override
    public Steam<T> sorted(Comparator<? super T> comparator) {
        return new Steam<>(stream.sorted(comparator));
    }

    /**
     * 返回与指定函数将元素作为参数执行后组成的流。
     * 这是一个无状态中间操作
     *
     * @param action 指定的函数
     * @return 返回叠加操作后的Steam
     * @apiNote 该方法存在的意义主要是用来调试
     * 当你需要查看经过操作管道某处的元素，可以执行以下操作:
     * <pre>{@code
     *     .of("one", "two", "three", "four")
     *         .filter(e -> e.length() > 3)
     *         .peek(e -> System.out.println("Filtered value: " + e))
     *         .map(String::toUpperCase)
     *         .peek(e -> System.out.println("Mapped value: " + e))
     *         .collect(Collectors.toList());
     * }</pre>
     */
    @Override
    public Steam<T> peek(Consumer<? super T> action) {
        return new Steam<>(stream.peek(action));
    }

    /**
     * 返回叠加调用{@link PrintStream#println(Object)}打印出结果的流
     *
     * @return 返回叠加操作后的Steam
     */
    public Steam<T> log() {
        return peek(System.out::println);
    }

    /**
     * 返回截取后面一些元素的流
     * 这是一个短路状态中间操作
     *
     * @param maxSize 元素截取后的个数
     * @return 截取后的流
     */
    @Override
    public Steam<T> limit(long maxSize) {
        return new Steam<>(stream.limit(maxSize));
    }

    /**
     * 返回丢弃前面n个元素后的剩余元素组成的流，如果当前元素个数小于n，则返回一个元素为空的流
     * 这是一个有状态中间操作
     *
     * @param n 需要丢弃的元素个数
     * @return 丢弃前面n个元素后的剩余元素组成的流
     */
    @Override
    public Steam<T> skip(long n) {
        return new Steam<>(stream.skip(n));
    }

    /**
     * 返回一个串行流，该方法可以将并行流转换为串行流
     *
     * @return 串行流
     */
    @Override
    public Steam<T> sequential() {
        //noinspection ResultOfMethodCallIgnored
        stream.sequential();
        return this;
    }

    /**
     * 对流里面的每一个元素执行一个操作
     * 这是一个终端操作
     *
     * @param action 操作
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        stream.forEach(action);
    }

    /**
     * 对流里面的每一个元素执行一个操作，操作带下标，并行流时下标永远为-1
     * 这是一个终端操作
     *
     * @param action 操作
     */
    public void forEachIdx(BiConsumer<? super T, Integer> action) {
        Objects.requireNonNull(action);
        if (isParallel()) {
            stream.forEach(e -> action.accept(e, NOT_FOUND_INDEX));
        } else {
            AtomicInteger index = new AtomicInteger(NOT_FOUND_INDEX);
            stream.forEach(e -> action.accept(e, index.incrementAndGet()));
        }
    }

    /**
     * 对流里面的每一个元素按照顺序执行一个操作
     * 这是一个终端操作
     *
     * @param action 操作
     */
    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        stream.forEachOrdered(action);
    }

    /**
     * 对流里面的每一个元素按照顺序执行一个操作，操作带下标，并行流时下标永远为-1
     * 这是一个终端操作
     *
     * @param action 操作
     */
    public void forEachOrderedIdx(BiConsumer<? super T, Integer> action) {
        Objects.requireNonNull(action);
        if (isParallel()) {
            stream.forEachOrdered(e -> action.accept(e, NOT_FOUND_INDEX));
        } else {
            AtomicInteger index = new AtomicInteger(NOT_FOUND_INDEX);
            stream.forEachOrdered(e -> action.accept(e, index.incrementAndGet()));
        }
    }

    /**
     * 返回一个包含此流元素的数组
     * 这是一个终端操作
     *
     * @return 包含此流元素的数组
     */
    @Override
    public Object[] toArray() {
        return stream.toArray();
    }

    /**
     * 返回一个包含此流元素的指定的数组
     *
     * @param generator 这里的IntFunction的参数是元素的个数，返回值为数组类型
     * @param <A>       给定的数组类型
     * @return 包含此流元素的指定的数组
     * @throws ArrayStoreException 如果元素转换失败，例如不是该元素类型及其父类，则抛出该异常
     *                             例如以下代码编译正常，但运行时会抛出 {@link ArrayStoreException}
     *                             <pre>{@code
     *                                                                                                                                                                                                                                                                                                                                                                                                                                                                 String[] strings = Stream.<Integer>builder().add(1).build().toArray(String[]::new);
     *                                                                                                                                                                                                                                                                                                                                                                                                                                                                 }</pre>
     */
    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        //noinspection SuspiciousToArrayCall
        return stream.toArray(generator);
    }

    /**
     * 对元素进行聚合，并返回聚合后的值，相当于在for循环里写sum=sum+ints[i]
     * 这是一个终端操作<br>
     * 求和、最小值、最大值、平均值和转换成一个String字符串均为聚合操作
     * 例如这里对int进行求和可以写成：
     *
     * <pre>{@code
     *     Integer sum = integers.reduce(0, (a, b) -> a+b);
     * }</pre>
     * <p>
     * 或者写成:
     *
     * <pre>{@code
     *     Integer sum = integers.reduce(0, Integer::sum);
     * }</pre>
     *
     * @param identity    初始值，还用于限定泛型
     * @param accumulator 你想要的聚合操作
     * @return 聚合计算后的值
     */
    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return stream.reduce(identity, accumulator);
    }

    /**
     * 对元素进行聚合，并返回聚合后用 {@link Optional}包裹的值，相当于在for循环里写sum=sum+ints[i]
     * 该操作相当于：
     * <pre>{@code
     *     boolean foundAny = false;
     *     T result = null;
     *     for (T element : this stream) {
     *         if (!foundAny) {
     *             foundAny = true;
     *             result = element;
     *         }
     *         else
     *             result = accumulator.apply(result, element);
     *     }
     *     return foundAny ? Optional.of(result) : Optional.empty();
     * }</pre>
     * 但它不局限于顺序执行，例如并行流等情况下
     * 这是一个终端操作<br>
     * 例如以下场景抛出 NPE ：
     * <pre>{@code
     *      Optional<Integer> reduce = Stream.<Integer>builder().add(1).add(1).build().reduce((a, b) -> null);
     * }</pre>
     *
     * @param accumulator 你想要的聚合操作
     * @return 聚合后用 {@link Optional}包裹的值
     * @throws NullPointerException 如果给定的聚合操作中执行后结果为空，并用于下一次执行，则抛出该异常
     * @see #reduce(Object, BinaryOperator)
     * @see #min(Comparator)
     * @see #max(Comparator)
     */
    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return stream.reduce(accumulator);
    }

    /**
     * 对元素进行聚合，并返回聚合后的值，并行流时聚合拿到的初始值不稳定
     * 这是一个终端操作
     *
     * @param identity    初始值
     * @param accumulator 累加器，具体为你要的聚合操作
     * @param combiner    用于并行流时组合多个结果
     * @param <U>         初始值
     * @return 聚合操作的结果
     * @see #reduce(BinaryOperator)
     * @see #reduce(Object, BinaryOperator)
     */
    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return stream.reduce(identity, accumulator, combiner);
    }

    /**
     * 对元素进行收集，并返回收集后的容器
     * 这是一个终端操作
     *
     * @param supplier    提供初始值的函数式接口，一般可以传入构造参数
     * @param accumulator 具体收集操作
     * @param combiner    用于并行流时组合多个结果
     * @param <R>         用于收集元素的容器，大多是集合
     * @return 收集后的容器
     * <pre>{@code
     *  List<Integer> collect = Stream.iterate(1, i -> ++i).limit(10).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
     * }</pre>
     */
    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return stream.collect(supplier, accumulator, combiner);
    }

    /**
     * 对元素进行收集，并返回收集后的元素
     * 这是一个终端操作
     *
     * @param collector 收集器
     * @param <R>       容器类型
     * @param <A>       具体操作时容器类型，例如 {@code List::add} 时它为 {@code List}
     * @return 收集后的容器
     */
    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return stream.collect(collector);
    }

    /**
     * 获取最小值
     *
     * @param comparator 一个用来比较大小的比较器{@link Comparator}
     * @return 最小值
     */
    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return stream.min(comparator);
    }

    /**
     * 获取最大值
     *
     * @param comparator 一个用来比较大小的比较器{@link Comparator}
     * @return 最大值
     */
    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return stream.max(comparator);
    }

    /**
     * 返回流元素个数
     *
     * @return 流元素个数
     */
    @Override
    public long count() {
        return stream.count();
    }

    /**
     * 判断是否有任何一个元素满足给定断言
     *
     * @param predicate 断言
     * @return 是否有任何一个元素满足给定断言
     */
    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return stream.anyMatch(predicate);
    }

    /**
     * 判断是否所有元素满足给定断言
     *
     * @param predicate 断言
     * @return 是否所有元素满足给定断言
     */
    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return stream.allMatch(predicate);
    }

    /**
     * 判断是否没有元素满足给定断言
     *
     * @param predicate 断言
     * @return 是否没有元素满足给定断言
     */
    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return stream.noneMatch(predicate);
    }

    /**
     * 获取第一个元素
     *
     * @return 第一个元素
     */
    @Override
    public Optional<T> findFirst() {
        return stream.findFirst();
    }

    /**
     * 获取与给定断言匹配的第一个元素
     *
     * @param predicate 断言
     * @return 与给定断言匹配的第一个元素
     */
    public T findFirst(Predicate<? super T> predicate) {
        return stream.filter(predicate).findFirst().orElse(null);
    }

    /**
     * 获取与给定断言匹配的第一个元素的下标，并行流下标永远为-1
     *
     * @param predicate 断言
     * @return 与给定断言匹配的第一个元素的下标，如果不存在则返回-1
     */
    public Integer findFirstIdx(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (isParallel()) {
            return NOT_FOUND_INDEX;
        } else {
            AtomicInteger index = new AtomicInteger(NOT_FOUND_INDEX);
            //noinspection ResultOfMethodCallIgnored
            stream.filter(e -> {
                index.incrementAndGet();
                return predicate.test(e);
            }).findFirst();
            return index.get();
        }
    }

    /**
     * 获取最后一个元素
     *
     * @return 最后一个元素
     */
    public Optional<T> findLast() {
        if (isParallel()) {
            return Optional.of(toList()).filter(l -> !l.isEmpty()).map(l -> l.get(l.size() - 1));
        } else {
            AtomicReference<T> last = new AtomicReference<>(null);
            forEach(last::set);
            return Optional.ofNullable(last.get());
        }
    }

    /**
     * 获取与给定断言匹配的最后一个元素
     *
     * @param predicate 断言
     * @return 与给定断言匹配的最后一个元素
     */
    public T findLast(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (isParallel()) {
            return filter(predicate).findLast().orElse(null);
        } else {
            AtomicReference<T> last = new AtomicReference<>(null);
            forEach(e -> {
                if (predicate.test(e)) {
                    last.set(e);
                }
            });
            return last.get();
        }
    }

    /**
     * 获取与给定断言匹配的最后一个元素的下标，并行流下标永远为-1
     *
     * @param predicate 断言
     * @return 与给定断言匹配的最后一个元素的下标，如果不存在则返回-1
     */
    public Integer findLastIdx(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (isParallel()) {
            return NOT_FOUND_INDEX;
        } else {
            AtomicInteger idxRef = new AtomicInteger(NOT_FOUND_INDEX);
            forEachIdx((e, i) -> {
                if (predicate.test(e)) {
                    idxRef.set(i);
                }
            });
            return idxRef.get();
        }
    }

    /**
     * 反转顺序
     *
     * @return 反转元素顺序
     */
    public Steam<T> reverse() {
        List<T> list = toList();
        Collections.reverse(list);
        return of(list, isParallel());
    }

    /**
     * 考虑性能，随便取一个，这里不是随机取一个，是随便取一个
     *
     * @return 随便取一个
     */
    @Override
    public Optional<T> findAny() {
        return stream.findAny();
    }

    /**
     * 返回流的迭代器
     *
     * @return 流的迭代器
     */
    @Override
    public Iterator<T> iterator() {
        return stream.iterator();
    }

    /**
     * 返回流的拆分器
     *
     * @return 流的拆分器
     */
    @Override
    public Spliterator<T> spliterator() {
        return stream.spliterator();
    }

    /**
     * 将流转换为并行
     *
     * @return 并行流
     */
    @Override
    public Steam<T> parallel() {
        //noinspection ResultOfMethodCallIgnored
        stream.parallel();
        return this;
    }

    /**
     * 更改流的并行状态
     *
     * @param parallel 是否并行
     * @return 流
     */
    public Steam<T> parallel(boolean parallel) {
        return parallel ? parallel() : sequential();
    }

    /**
     * 返回一个无序流(无手动排序)
     *
     * @return 无序流
     */
    @Override
    public Steam<T> unordered() {
        return new Steam<>(stream.unordered());
    }

    /**
     * 在流关闭时执行操作
     *
     * @param closeHandler 在流关闭时执行的操作
     * @return 流
     */
    @Override
    public Steam<T> onClose(Runnable closeHandler) {
        //noinspection ResultOfMethodCallIgnored
        stream.onClose(closeHandler);
        return this;
    }

    /**
     * 与给定元素组成的流合并，成为新的流
     *
     * @param obj 元素
     * @return 流
     */
    public Steam<T> push(T obj) {
        return Steam.concat(this.stream, Stream.of(obj));
    }

    /**
     * 与给定元素组成的流合并，成为新的流
     *
     * @param obj 元素
     * @return 流
     */
    @SuppressWarnings("unchecked")
    public Steam<T> push(T... obj) {
        return Steam.concat(this.stream, of(obj));
    }

    /**
     * 给定元素组成的流与当前流合并，成为新的流
     *
     * @param obj 元素
     * @return 流
     */
    public Steam<T> unshift(T obj) {
        return Steam.concat(Stream.of(obj), this.stream);
    }

    /**
     * 给定元素组成的流与当前流合并，成为新的流
     *
     * @param obj 元素
     * @return 流
     */
    @SafeVarargs
    public final Steam<T> unshift(T... obj) {
        return Steam.concat(of(obj), this.stream);
    }

    /**
     * 获取流中指定下标的元素，如果是负数，则从最后一个开始数起
     *
     * @param idx 下标
     * @return 指定下标的元素
     */
    public T at(Integer idx) {
        if (Objects.isNull(idx)) {
            return null;
        }
        List<T> list = toList();
        if (idx > -1) {
            if (idx >= list.size()) {
                return null;
            }
            return list.get(idx);
        }
        if (-idx > list.size()) {
            return null;
        }
        return list.get(list.size() + idx);
    }

    /**
     * 返回流的并行状态
     *
     * @return 流的并行状态
     */
    @Override
    public boolean isParallel() {
        return stream.isParallel();
    }

    /**
     * 关闭流
     *
     * @see AutoCloseable#close()
     */
    @Override
    public void close() {
        stream.close();
    }

    /**
     * hashcode
     *
     * @return hashcode
     */
    @Override
    public int hashCode() {
        return stream.hashCode();
    }

    /**
     * equals
     *
     * @param obj 对象
     * @return 结果
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Stream) {
            return stream.equals(obj);
        }
        return false;
    }

    /**
     * toString
     *
     * @return string
     */
    @Override
    public String toString() {
        return stream.toString();
    }

    /**
     * 转换成集合
     *
     * @param collectionFactory 集合工厂(可以是集合构造器)
     * @param <C>               集合类型
     * @return 集合
     */
    public <C extends Collection<T>> C toColl(Supplier<C> collectionFactory) {
        return collect(Collective.toCollection(collectionFactory));
    }

    /**
     * 转换为ArrayList
     *
     * @return list
     */
    public List<T> toList() {
        return collect(Collective.toList());
    }

    /**
     * 转换为HashSet
     *
     * @return hashSet
     */
    public Set<T> toSet() {
        return collect(Collective.toSet());
    }

    /**
     * 与给定的可迭代对象转换成map，key为现有元素，value为给定可迭代对象迭代的元素<br>
     * 至少包含全部的key，如果对应位置上的value不存在，则为null
     *
     * @param other 可迭代对象
     * @param <R>   可迭代对象迭代的元素类型
     * @return map，key为现有元素，value为给定可迭代对象迭代的元素;<br>
     * 至少包含全部的key，如果对应位置上的value不存在，则为null;<br>
     * 如果key重复, 则保留最后一个关联的value;<br>
     */
    public <R> Map<T, R> toZip(Iterable<R> other) {
        // value对象迭代器
        final Iterator<R> iterator = Opp.of(other).map(Iterable::iterator).orElseGet(Collections::emptyIterator);
        if (isParallel()) {
            List<T> keyList = toList();
            final Map<T, R> map = new HashMap<>(keyList.size());
            for (T key : keyList) {
                map.put(key, iterator.hasNext() ? iterator.next() : null);
            }
            return map;
        } else {
            return toMap(Function.identity(), e -> iterator.hasNext() ? iterator.next() : null);
        }
    }

    /**
     * 返回拼接后的字符串
     *
     * @return 拼接后的字符串
     */
    public String join() {
        return join("");
    }

    /**
     * 返回拼接后的字符串
     *
     * @param delimiter 分隔符
     * @return 拼接后的字符串
     */
    public String join(CharSequence delimiter) {
        return join(delimiter, "", "");
    }

    /**
     * 返回拼接后的字符串
     *
     * @param delimiter 分隔符
     * @param prefix    前缀
     * @param suffix    后缀
     * @return 拼接后的字符串
     */
    public String join(CharSequence delimiter,
                       CharSequence prefix,
                       CharSequence suffix) {
        return map(String::valueOf).collect(Collective.joining(delimiter, prefix, suffix));
    }

    /**
     * 转换为map，key为给定操作执行后的返回值,value为当前元素
     *
     * @param keyMapper 指定的key操作
     * @param <K>       key类型
     * @return map
     */
    public <K> Map<K, T> toMap(Function<? super T, ? extends K> keyMapper) {
        return toMap(keyMapper, Function.identity());
    }

    /**
     * 转换为map，key,value为给定操作执行后的返回值
     *
     * @param keyMapper   指定的key操作
     * @param valueMapper 指定value操作
     * @param <K>         key类型
     * @param <U>         value类型
     * @return map
     */
    public <K, U> Map<K, U> toMap(Function<? super T, ? extends K> keyMapper,
                                  Function<? super T, ? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper, (l, r) -> r);
    }

    /**
     * 转换为map，key,value为给定操作执行后的返回值
     *
     * @param keyMapper     指定的key操作
     * @param valueMapper   指定value操作
     * @param mergeFunction 合并操作
     * @param <K>           key类型
     * @param <U>           value类型
     * @return map
     */
    public <K, U> Map<K, U> toMap(Function<? super T, ? extends K> keyMapper,
                                  Function<? super T, ? extends U> valueMapper,
                                  BinaryOperator<U> mergeFunction) {
        return toMap(keyMapper, valueMapper, mergeFunction, HashMap::new);
    }

    /**
     * 转换为map，key,value为给定操作执行后的返回值
     *
     * @param keyMapper     指定的key操作
     * @param valueMapper   指定value操作
     * @param mergeFunction 合并操作
     * @param mapSupplier   map工厂
     * @param <K>           key类型
     * @param <U>           value类型
     * @param <M>           map类型
     * @return map
     */
    public <K, U, M extends Map<K, U>> M toMap(Function<? super T, ? extends K> keyMapper,
                                               Function<? super T, ? extends U> valueMapper,
                                               BinaryOperator<U> mergeFunction,
                                               Supplier<M> mapSupplier) {
        return collect(Collective.toMap(keyMapper, valueMapper, mergeFunction, mapSupplier));
    }


    /**
     * 通过给定分组依据进行分组
     *
     * @param classifier 分组依据
     * @param <K>        实体中的分组依据对应类型，也是Map中key的类型
     * @return {@link Collector}
     */
    public <K> Map<K, List<T>> group(Function<? super T, ? extends K> classifier) {
        return group(classifier, Collective.toList());
    }

    /**
     * 通过给定分组依据进行分组
     *
     * @param classifier 分组依据
     * @param downstream 下游操作
     * @param <K>        实体中的分组依据对应类型，也是Map中key的类型
     * @param <D>        下游操作对应返回类型，也是Map中value的类型
     * @param <A>        下游操作在进行中间操作时对应类型
     * @return {@link Collector}
     */
    public <K, A, D> Map<K, D> group(Function<? super T, ? extends K> classifier,
                                     Collector<? super T, A, D> downstream) {
        return group(classifier, HashMap::new, downstream);
    }

    /**
     * 通过给定分组依据进行分组
     *
     * @param classifier 分组依据
     * @param mapFactory 提供的map
     * @param downstream 下游操作
     * @param <K>        实体中的分组依据对应类型，也是Map中key的类型
     * @param <D>        下游操作对应返回类型，也是Map中value的类型
     * @param <A>        下游操作在进行中间操作时对应类型
     * @param <M>        最后返回结果Map类型
     * @return {@link Collector}
     */
    public <K, D, A, M extends Map<K, D>> M group(Function<? super T, ? extends K> classifier,
                                                  Supplier<M> mapFactory,
                                                  Collector<? super T, A, D> downstream) {
        return collect(Collective.groupingBy(classifier, mapFactory, downstream));
    }

    /**
     * 将 现有元素 与 给定迭代器中对应位置的元素 使用 zipper 转换为新的元素，并返回新元素组成的流<br>
     * 新流的数量等于旧流元素的数量<br>
     * 使用 zipper 转换时, 如果对应位置上已经没有other元素，则other元素为null<br>
     *
     * @param other  给定的迭代器
     * @param zipper 两个元素的合并器
     * @param <U>    给定的迭代对象类型
     * @param <R>    合并后的结果对象类型
     * @return 合并后的结果对象的流
     */
    public <U, R> Steam<R> zip(Iterable<U> other,
                               BiFunction<? super T, ? super U, ? extends R> zipper) {
        Objects.requireNonNull(zipper);
        // 给定对象迭代器
        final Iterator<U> iterator = Opp.of(other).map(Iterable::iterator).orElseGet(Collections::emptyIterator);
        Stream<T> resStream = this.stream;
        if (isParallel()) {
            resStream = toList().stream();
        }
        final Steam<R> newStream = of(resStream.map(e -> zipper.apply(e, iterator.hasNext() ? iterator.next() : null)));
        newStream.parallel(isParallel());
        return newStream;
    }

    /**
     * 类似js的<a href="https://developer.mozilla.org/zh-CN/docs/Web/JavaScript/Reference/Global_Objects/Array/splice">splice</a>函数
     *
     * @param start       起始下标
     * @param deleteCount 删除个数，正整数
     * @param items       放入值
     * @return 操作后的流
     */
    @SuppressWarnings("unchecked")
    public Steam<T> splice(int start, int deleteCount, T... items) {
        List<T> list = toList();
        final int size = list.size();
        // 从后往前查找
        if (start < 0) {
            start += size;
        } else if (start >= size) {
            // 直接在尾部追加，不删除
            start = size;
            deleteCount = 0;
        }
        // 起始位置 加上 删除的数量 超过 数据长度，需要重新计算需要删除的数量
        if (start + deleteCount > size) {
            deleteCount = size - start;
        }

        // 新列表的长度
        final int newSize = size - deleteCount + items.length;
        List<T> resList = list;
        // 新列表的长度 大于 旧列表，创建新列表
        if (newSize > size) {
            resList = new ArrayList<>(newSize);
            resList.addAll(list);
        }
        // 需要删除的部分
        if (deleteCount > 0) {
            resList.subList(start, start + deleteCount).clear();
        }
        // 新增的部分
        if (items.length > 0) {
            resList.addAll(start, Arrays.asList(items));
        }
        return Steam.of(resList);
    }

    /**
     * 按指定长度切分为双层流
     * <p>
     * 形如：[1,2,3,4,5] -> [[1,2], [3,4], [5,6]]
     * </p>
     *
     * @param batchSize 指定长度, 正整数
     * @return 切好的流
     */
    public Steam<Steam<T>> split(final int batchSize) {
        List<T> list = toList();
        final int size = list.size();
        // 指定长度 大于等于 列表长度
        if (size <= batchSize) {
            // 返回第一层只有单个元素的双层流，形如：[[1,2,3,4,5]]
            return Steam.<Steam<T>>of(of(list, isParallel()));
        }
        return Steam.iterate(0, i -> i < size, i -> i + batchSize)
                .map(skip -> of(list.subList(skip, Math.min(size, skip + batchSize)), isParallel()))
                .parallel(isParallel());
    }

    /**
     * 按指定长度切分为元素为list的流
     * <p>
     * 形如：[1,2,3,4,5] -> [[1,2], [3,4], [5,6]]
     * </p>
     *
     * @param batchSize 指定长度, 正整数
     * @return 切好的流
     */
    public Steam<List<T>> splitList(final int batchSize) {
        return split(batchSize).map(Steam::toList);
    }

    public interface Builder<T> extends Consumer<T> {

        /**
         * Adds an element to the stream being built.
         *
         * @param t the element to add
         * @throws IllegalStateException if the builder has already transitioned to
         *                               the built state
         */
        @Override
        void accept(T t);

        /**
         * Adds an element to the stream being built.
         *
         * @param t the element to add
         * @return {@code this} builder
         * @throws IllegalStateException if the builder has already transitioned to
         *                               the built state
         * @implSpec The default implementation behaves as if:
         * <pre>{@code
         *     accept(t)
         *     return this;
         * }</pre>
         */
        default Builder<T> add(T t) {
            accept(t);
            return this;
        }

        /**
         * Builds the stream, transitioning this builder to the built state.
         * An {@code IllegalStateException} is thrown if there are further attempts
         * to operate on the builder after it has entered the built state.
         *
         * @return the built stream
         * @throws IllegalStateException if the builder has already transitioned to
         *                               the built state
         */
        Steam<T> build();

    }

}
