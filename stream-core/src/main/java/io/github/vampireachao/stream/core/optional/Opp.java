package io.github.vampireachao.stream.core.optional;

import io.github.vampireachao.stream.core.lambda.LambdaExecutable;
import io.github.vampireachao.stream.core.lambda.LambdaHelper;
import io.github.vampireachao.stream.core.lambda.function.SerCons;
import io.github.vampireachao.stream.core.lambda.function.SerFunc;
import io.github.vampireachao.stream.core.lambda.function.SerPred;
import io.github.vampireachao.stream.core.reflect.ReflectHelper;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

/**
 * 拓展hutool中的Opt
 *
 * @param <T> 包裹里元素的类型
 * @author VampireAchao
 * @see java.util.Optional
 */
public class Opp<T> {
    /**
     * 一个空的{@code Opp}
     */
    private static final Opp<?> EMPTY = new Opp<>(null);
    /**
     * 包裹里实际的元素
     */
    private final T value;
    private Exception exception;

    /**
     * {@code Opp}的构造函数
     *
     * @param value 包裹里的元素
     */
    private Opp(T value) {
        this.value = value;
    }

    /**
     * 返回一个空的{@code Opp}
     *
     * @param <T> 包裹里元素的类型
     * @return Opp
     */
    public static <T> Opp<T> empty() {
        @SuppressWarnings("unchecked") final Opp<T> t = (Opp<T>) EMPTY;
        return t;
    }

    /**
     * 返回一个包裹里元素不可能为空的{@code Opp}
     *
     * @param value 包裹里的元素
     * @param <T>   包裹里元素的类型
     * @return 一个包裹里元素不可能为空的 {@code Opp}
     * @throws NullPointerException 如果传入的元素为空，抛出 {@code NPE}
     */
    public static <T> Opp<T> required(T value) {
        return new Opp<>(Objects.requireNonNull(value));
    }

    /**
     * 返回一个包裹里元素可能为空的{@code Opp}
     *
     * @param value 传入需要包裹的元素
     * @param <T>   包裹里元素的类型
     * @return 一个包裹里元素可能为空的 {@code Opp}
     */
    public static <T> Opp<T> of(T value) {
        return value == null ? empty()
                : new Opp<>(value);
    }

    /**
     * 返回一个包裹里元素可能为空的{@code Opp}，额外判断了空字符串的情况
     *
     * @param value 传入需要包裹的元素
     * @return 一个包裹里元素可能为空，或者为空字符串的 {@code Opp}
     */
    public static <T extends CharSequence> Opp<T> blank(T value) {
        return Opp.of(value).filter(str -> !str.toString().trim().isEmpty());
    }

    /**
     * +
     * 返回一个包裹里{@code List}集合可能为空的{@code Opp}，额外判断了集合内元素为空的情况
     *
     * @param <T>   包裹里元素的类型
     * @param <R>   集合值类型
     * @param value 传入需要包裹的元素
     * @return 一个包裹里元素可能为空的 {@code Opp}
     */
    public static <T, R extends Collection<T>> Opp<R> empty(R value) {
        return Opp.of(value).filter(coll -> !coll.isEmpty() && !Objects.equals(Collections.frequency(value, null), value.size()));
    }

    /**
     * @param supplier 操作
     * @param <T>      类型
     * @return 操作执行后的值
     */
    public static <T> Opp<T> ofTry(Supplier<T> supplier) {
        try {
            return Opp.of(supplier.get());
        } catch (Exception e) {
            final Opp<T> empty = new Opp<>(null);
            empty.exception = e;
            return empty;
        }
    }

    /**
     * 返回包裹里的元素，取不到则为{@code null}，注意！！！此处和{@link java.util.Optional#get()}不同的一点是本方法并不会抛出{@code NoSuchElementException}
     * 如果元素为空，则返回{@code null}，如果需要一个绝对不能为{@code null}的值，则使用{@link #orElseThrow()}
     *
     * <p>
     * 如果需要一个绝对不能为 {@code null}的值，则使用{@link #orElseThrow()}
     * 做此处修改的原因是，有时候我们确实需要返回一个null给前端，并且这样的时候并不少见
     * 而使用 {@code .orElse(null)}需要写整整12个字符，用{@code .get()}就只需要6个啦
     *
     * @return 包裹里的元素，有可能为{@code null}
     */
    public T get() {
        return this.value;
    }

    public <R> R get(Function<T, R> mapper) {
        return map(mapper).orElse(null);
    }

    /**
     * 判断包裹里元素的值是否不存在，不存在为 {@code true}，否则为{@code false}
     *
     * @return 包裹里元素的值不存在 则为 {@code true}，否则为{@code false}
     */
    public boolean isNull() {
        return value == null;
    }

    /**
     * 获取异常<br>
     * 当调用 {@link #ofTry(Supplier)}时，异常信息不会抛出，而是保存，调用此方法获取抛出的异常
     *
     * @return 异常
     */
    public Exception getException() {
        return this.exception;
    }

    /**
     * 是否失败<br>
     * 当调用 {@link #ofTry(Supplier)}时，抛出异常则表示失败
     *
     * @return 是否失败
     */
    public boolean isFail() {
        return null != this.exception;
    }

    /**
     * 判断包裹里元素的值是否存在，存在为 {@code true}，否则为{@code false}
     *
     * @return 包裹里元素的值存在为 {@code true}，否则为{@code false}
     */
    public boolean isNonNull() {
        return value != null;
    }

    /**
     * 如果包裹里的值存在，就执行传入的操作({@link Consumer#accept})
     *
     * <p> 例如如果值存在就打印结果
     * <pre>{@code
     * Opp.ofNullable("Hello Hutool!").ifPresent(Console::log);
     * }</pre>
     *
     * @param action 你想要执行的操作
     * @return this
     * @throws NullPointerException 如果包裹里的值存在，但你传入的操作为{@code null}时抛出
     */
    public Opp<T> ifPresent(Consumer<? super T> action) {
        if (isNonNull()) {
            action.accept(value);
        }
        return this;
    }

    /**
     * 判断包裹里的值存在并且与给定的条件是否满足 ({@link Predicate#test}执行结果是否为true)
     * 如果满足条件则返回本身
     * 不满足条件或者元素本身为空时返回一个返回一个空的{@code Opp}
     *
     * @param predicate 给定的条件
     * @return 如果满足条件则返回本身, 不满足条件或者元素本身为空时返回一个返回一个空的{@code Opp}
     * @throws NullPointerException 如果给定的条件为 {@code null}，抛出{@code NPE}
     */
    public Opp<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        if (isNull()) {
            return this;
        } else {
            return predicate.test(value) ? this : empty();
        }
    }

    /**
     * 如果包裹里的值存在，就执行传入的操作({@link Function#apply})并返回一个包裹了该操作返回值的{@code Opp}
     * 如果不存在，返回一个空的{@code Opp}
     *
     * @param mapper 值存在时执行的操作
     * @param <U>    操作返回值的类型
     * @return 如果包裹里的值存在，就执行传入的操作({@link Function#apply})并返回一个包裹了该操作返回值的{@code Opp}，
     * 如果不存在，返回一个空的{@code Opp}
     * @throws NullPointerException 如果给定的操作为 {@code null}，抛出 {@code NPE}
     */
    public <U> Opp<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper);
        if (isNull()) {
            return empty();
        } else {
            return Opp.of(mapper.apply(value));
        }
    }

    /**
     * 如果包裹里的值存在，就执行传入的操作({@link Function#apply})并返回该操作返回值
     * 如果不存在，返回一个空的{@code Opp}
     * 和 {@link Opp#map}的区别为 传入的操作返回值必须为 Opp
     *
     * @param mapper 值存在时执行的操作
     * @param <U>    操作返回值的类型
     * @return 如果包裹里的值存在，就执行传入的操作({@link Function#apply})并返回该操作返回值
     * 如果不存在，返回一个空的{@code Opp}
     * @throws NullPointerException 如果给定的操作为 {@code null}或者给定的操作执行结果为 {@code null}，抛出 {@code NPE}
     */
    public <U> Opp<U> flatMap(Function<? super T, ? extends Opp<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        if (isNull()) {
            return empty();
        } else {
            @SuppressWarnings("unchecked") final Opp<U> r = (Opp<U>) mapper.apply(value);
            return Objects.requireNonNull(r);
        }
    }

    /**
     * 如果包裹里的值存在，就执行传入的操作({@link Function#apply})并返回该操作返回值
     * 如果不存在，返回一个空的{@code Opp}
     * 和 {@link Opp#map}的区别为 传入的操作返回值必须为 {@link Optional}
     *
     * @param mapper 值存在时执行的操作
     * @param <U>    操作返回值的类型
     * @return 如果包裹里的值存在，就执行传入的操作({@link Function#apply})并返回该操作返回值
     * 如果不存在，返回一个空的{@code Opp}
     * @throws NullPointerException 如果给定的操作为 {@code null}或者给定的操作执行结果为 {@code null}，抛出 {@code NPE}
     * @see Optional#flatMap(Function)
     */
    public <U> Opp<U> flattedMap(Function<? super T, Optional<? extends U>> mapper) {
        Objects.requireNonNull(mapper);
        if (isNull()) {
            return empty();
        } else {
            return of(mapper.apply(value).orElse(null));
        }
    }

    /**
     * 如果包裹里元素的值存在，就执行对应的操作，并返回本身
     * 如果不存在，返回一个空的{@code Opp}
     *
     * <p>属于 {@link #ifPresent}的链式拓展
     *
     * @param action 值存在时执行的操作
     * @return this
     * @throws NullPointerException 如果值存在，并且传入的操作为 {@code null}
     */
    public Opp<T> peek(Consumer<T> action) throws NullPointerException {
        Objects.requireNonNull(action);
        if (isNull()) {
            return Opp.empty();
        }
        action.accept(value);
        return this;
    }


    /**
     * 如果包裹里元素的值存在，就执行对应的操作集，并返回本身
     * 如果不存在，返回一个空的{@code Opp}
     *
     * <p>属于 {@link #ifPresent}的链式拓展
     * <p>属于 {@link #peek(Consumer)}的动态拓展
     *
     * @param actions 值存在时执行的操作，动态参数，可传入数组，当数组为一个空数组时并不会抛出 {@code NPE}
     * @return this
     * @throws NullPointerException 如果值存在，并且传入的操作集中的元素为 {@code null}
     */
    @SafeVarargs
    public final Opp<T> peeks(Consumer<T>... actions) throws NullPointerException {
        return peek(Stream.of(actions).reduce(Consumer::andThen).orElseGet(() -> o -> {}));
    }

    /**
     * 如果传入的lambda入参类型一致，或者是父类，就执行，目前不支持子泛型
     *
     * @param action 入参类型一致，或者是父类，就执行的操作
     * @param <U>    操作入参类型
     * @return 如果传入的lambda入参类型一致，就执行对应的操作，并返回本身
     */
    public <U> Opp<T> typeOfPeek(SerCons<U> action) {
        return ofTry(() -> {
            LambdaExecutable resolve = LambdaHelper.resolve(action);
            Type[] types = resolve.getParameterTypes();
            return types[types.length - 1];
        }).flatMap(type -> typeOfPeek(type, action));
    }

    /**
     * 如果传入的lambda入参类型一致，或者是父类，就执行并获取返回值，目前不支持子泛型
     *
     * @param mapper 入参类型一致，或者是父类，就执行的操作
     * @param <U>    操作入参类型
     * @param <R>    操作返回值类型
     * @return 如果传入的lambda入参类型一致，就执行并获取返回值
     */
    public <U, R> Opp<R> typeOfMap(SerFunc<U, R> mapper) {
        return ofTry(() -> {
            Type[] types = LambdaHelper.resolve(mapper).getParameterTypes();
            return types[types.length - 1];
        }).flatMap(type -> typeOfMap(type, mapper));
    }

    /**
     * 判断如果传入的类型一致，或者是父类，并且包裹里的值存在，并且与给定的条件是否满足 ({@link Predicate#test}执行结果是否为true)
     * 如果满足条件则返回本身
     * 不满足条件或者元素本身为空时返回一个返回一个空的{@code Opp}
     *
     * @param predicate 给定的条件
     * @return 如果满足条件则返回本身, 不满足条件或者元素本身为空时返回一个返回一个空的{@code Opp}
     * @throws NullPointerException 如果给定的条件为 {@code null}，抛出{@code NPE}
     */
    public <U> Opp<T> typeOfFilter(SerPred<U> predicate) {
        return ofTry(() -> {
            Type[] types = LambdaHelper.resolve(predicate).getParameterTypes();
            return types[types.length - 1];
        }).flatMap(type -> typeOfFilter(type, predicate));
    }

    /**
     * 如果传入的类型一致，或者是父类，就执行，支持子泛型
     *
     * @param type   类型
     * @param action 入参类型一致，或者是父类，就执行的操作
     * @param <U>    操作入参类型
     * @return 如果传入的lambda入参类型一致，就执行对应的操作，并返回本身
     */
    @SuppressWarnings("unchecked")
    public <U> Opp<T> typeOfPeek(Type type, SerCons<U> action) {
        return of(type).flatMap(t -> filter(obj -> ReflectHelper.isInstance(obj, t)).peek(v -> action.accept((U) v)));
    }

    /**
     * 如果传入的类型一致，或者是父类，就执行并获取返回值，目前不支持子泛型
     *
     * @param type   类型
     * @param mapper 入参类型一致，或者是父类，就执行的操作
     * @param <U>    操作入参类型
     * @param <R>    操作返回值类型
     * @return 如果传入的lambda入参类型一致，就执行并获取返回值
     */
    @SuppressWarnings("unchecked")
    public <U, R> Opp<R> typeOfMap(Type type, SerFunc<U, R> mapper) {
        return of(type).flatMap(t -> filter(obj -> ReflectHelper.isInstance(obj, t)).map(v -> mapper.apply((U) v)));
    }

    /**
     * 判断如果传入的类型一致，或者是父类，并且包裹里的值存在，并且与给定的条件是否满足 ({@link Predicate#test}执行结果是否为true)
     * 如果满足条件则返回本身
     * 不满足条件或者元素本身为空时返回一个返回一个空的{@code Opp}
     *
     * @param type      类型
     * @param predicate 给定的条件
     * @return 如果满足条件则返回本身, 不满足条件或者元素本身为空时返回一个返回一个空的{@code Opp}
     * @throws NullPointerException 如果给定的条件为 {@code null}，抛出{@code NPE}
     */
    @SuppressWarnings("unchecked")
    public <U> Opp<T> typeOfFilter(Type type, SerPred<U> predicate) {
        return of(type).flatMap(t -> filter(obj -> ReflectHelper.isInstance(obj, t)).filter(v -> predicate.test((U) v)));
    }

    /**
     * 如果包裹里元素的值存在，就返回本身，如果不存在，则使用传入的操作执行后获得的 {@code Opp}
     *
     * @param supplier 不存在时的操作
     * @return 如果包裹里元素的值存在，就返回本身，如果不存在，则使用传入的函数执行后获得的 {@code Opp}
     * @throws NullPointerException 如果传入的操作为空，或者传入的操作执行后返回值为空，则抛出 {@code NPE}
     */
    public Opp<T> or(Supplier<? extends Opp<? extends T>> supplier) {
        Objects.requireNonNull(supplier);
        if (isNonNull()) {
            return this;
        } else {
            @SuppressWarnings("unchecked") final Opp<T> r = (Opp<T>) supplier.get();
            return Objects.requireNonNull(r);
        }
    }

    /**
     * 如果包裹里元素的值存在，就返回一个包含该元素的 {@link Stream},
     * 否则返回一个空元素的 {@link Stream}
     *
     * <p> 该方法能将 Opp 中的元素传递给 {@link Stream}
     * <pre>{@code
     *     Stream<Opp<T>> os = ..
     *     Stream<T> s = os.flatMap(Opp::stream)
     * }</pre>
     *
     * @return 返回一个包含该元素的 {@link Stream}或空的 {@link Stream}
     */
    public Stream<T> stream() {
        if (isNull()) {
            return Stream.empty();
        } else {
            return Stream.of(value);
        }
    }

    /**
     * 如果包裹里元素的值存在，则返回该值，否则返回传入的{@code other}
     *
     * @param other 元素为空时返回的值，有可能为 {@code null}.
     * @return 如果包裹里元素的值存在，则返回该值，否则返回传入的{@code other}
     */
    public T orElse(T other) {
        return isNonNull() ? value : other;
    }

    /**
     * 如果包裹里元素的值存在，则返回该值，否则执行传入的操作
     *
     * @param action 值不存在时执行的操作
     * @return 如果包裹里元素的值存在，则返回该值，否则执行传入的操作
     * @throws NullPointerException 如果值不存在，并且传入的操作为 {@code null}
     */
    public <R extends Runnable> T orElseRun(R action) {
        if (isNonNull()) {
            return value;
        } else {
            action.run();
            return null;
        }
    }

    /**
     * 异常则返回另一个可选值
     *
     * @param other 可选值
     * @return 如果未发生异常，则返回该值，否则返回传入的{@code other}
     */
    public T failOrElse(T other) {
        return isFail() ? other : value;
    }

    /**
     * 如果包裹里元素的值存在，则返回该值，否则返回传入的操作执行后的返回值
     *
     * @param supplier 值不存在时需要执行的操作，返回一个类型与 包裹里元素类型 相同的元素
     * @return 如果包裹里元素的值存在，则返回该值，否则返回传入的操作执行后的返回值
     * @throws NullPointerException 如果之不存在，并且传入的操作为空，则抛出 {@code NPE}
     */
    public T orElseGet(Supplier<? extends T> supplier) {
        return isNonNull() ? value : supplier.get();
    }

    /**
     * 如果包裹里的值存在，则返回该值，否则抛出 {@code NoSuchElementException}
     *
     * @return 返回一个不为 {@code null} 的包裹里的值
     * @throws NoSuchElementException 如果包裹里的值不存在则抛出该异常
     */
    public T orElseThrow() {
        return orElseThrow(NoSuchElementException::new, "No value present");
    }

    /**
     * 如果包裹里的值存在，则返回该值，否则执行传入的操作，获取异常类型的返回值并抛出
     * <p>往往是一个包含无参构造器的异常 例如传入{@code IllegalStateException::new}
     *
     * @param <X>               异常类型
     * @param exceptionSupplier 值不存在时执行的操作，返回值继承 {@link Throwable}
     * @return 包裹里不能为空的值
     * @throws X                    如果值不存在
     * @throws NullPointerException 如果值不存在并且 传入的操作为 {@code null}或者操作执行后的返回值为{@code null}
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (isNonNull()) {
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }

    /**
     * 如果包裹里的值存在，则返回该值，否则执行传入的操作，获取异常类型的返回值并抛出
     *
     * <p>往往是一个包含 自定义消息 构造器的异常 例如
     * <pre>{@code
     * 		Opp.ofNullable(null).orElseThrow(IllegalStateException::new, "Ops!Something is wrong!");
     * }</pre>
     *
     * @param <X>               异常类型
     * @param exceptionFunction 值不存在时执行的操作，返回值继承 {@link Throwable}
     * @param message           作为传入操作执行时的参数，一般作为异常自定义提示语
     * @return 包裹里不能为空的值
     * @throws X                    如果值不存在
     * @throws NullPointerException 如果值不存在并且 传入的操作为 {@code null}或者操作执行后的返回值为{@code null}
     */
    public <X extends Throwable> T orElseThrow(Function<String, ? extends X> exceptionFunction, String message) throws X {
        if (isNonNull()) {
            return value;
        } else {
            throw exceptionFunction.apply(message);
        }
    }

    /**
     * 转换为 {@link Optional}对象
     *
     * @return {@link Optional}对象
     */
    public Optional<T> toOptional() {
        return Optional.ofNullable(this.value);
    }


    /**
     * 判断传入参数是否与 {@code Opp}相等
     * 在以下情况下返回true
     * <ul>
     * <li>它也是一个 {@code Opp} 并且
     * <li>它们包裹住的元素都为空 或者
     * <li>它们包裹住的元素之间相互 {@code equals()}
     * </ul>
     *
     * @param obj 一个要用来判断是否相等的参数
     * @return 如果传入的参数也是一个 {@code Opp}并且它们包裹住的元素都为空
     * 或者它们包裹住的元素之间相互 {@code equals()} 就返回{@code true}
     * 否则返回 {@code false}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Opp)) {
            return false;
        }

        final Opp<?> other = (Opp<?>) obj;
        return Objects.equals(value, other.value);
    }

    /**
     * 如果包裹内元素为空，则返回0，否则返回元素的 {@code hashcode}
     *
     * @return 如果包裹内元素为空，则返回0，否则返回元素的 {@code hashcode}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /**
     * 返回包裹内元素调用{@code toString()}的结果，不存在则返回{@code null}
     *
     * @return 包裹内元素调用{@code toString()}的结果，不存在则返回{@code null}
     */
    @Override
    public String toString() {
        return isNonNull() ? value.toString() : null;
    }

    public <R> Opp<T> filterEqual(R value) {
        return filter(Predicate.isEqual(value));
    }

    public <R> boolean isEqual(R value) {
        return filterEqual(value).isNonNull();
    }

    public boolean is(Predicate<T> predicate) {
        return filter(predicate).isNonNull();
    }

    public <R> Opp<R> zip(Opp<R> other, BiFunction<T, R, R> mapper) {
        Objects.requireNonNull(mapper);
        if (isNull() || other.isNull()) {
            return empty();
        } else {
            return Opp.of(mapper.apply(value, other.value));
        }
    }

    public Opp<T> zipOrSelf(Opp<T> other, BinaryOperator<T> mapper) {
        Objects.requireNonNull(mapper);
        if (isNull()) {
            return empty();
        } else if (other.isNull()) {
            return this;
        } else {
            return Opp.of(mapper.apply(value, other.value));
        }
    }
}