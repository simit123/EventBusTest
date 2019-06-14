package com.example.a860617202.eventbustest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {

    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv = ((TextView) findViewById(R.id.tv));

    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getMessage( MessageEvent messageEvent){
            tv.setText(messageEvent.getMessage());
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        MessageEvent messageEvent = new MessageEvent("eventBus");
        EventBus.getDefault().post(messageEvent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    /**
     *  EVENTBUS 源码分析：
     *  1.注册
     *  2.发送
     *  3.解除注册
     *  1.注册：
     *   EventBus.getDefault().register(this);
     *   getDefault()方法 目的是为了获取一个单例的EventBus对象
     *      public static EventBus getDefault() {
     *    if (defaultInstance == null) {
     *    synchronized (EventBus.class) {
     *    if (defaultInstance == null) {
     *    defaultInstance = new EventBus();
     *   }
     *   }
     *    }
     *   return defaultInstance;
     *   }
     *
     *   EventBus构建时调用当前构造并传入 DEFAULT_BUILDER
     *
     *    public EventBus() {
        this(DEFAULT_BUILDER);
        }

             EventBus(EventBusBuilder builder) {
             logger = builder.getLogger();//eventBus日志
            //这三个核心成员变量
             subscriptionsByEventType = new HashMap<>();//Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType  CopyOnWriteArrayList线程安全的list集合 用来存储订阅方法的class 和 订阅者和订阅方法的关系
             typesBySubscriber = new HashMap<>();//Map<Object, List<Class<?>>> typesBySubscriber 维护的是订阅者和订阅方法之间的对应关系
             stickyEvents = new ConcurrentHashMap<>();//Map<Class<?>, Object> stickyEvents

            //eventBus线程间的调度 搞清楚这四种分别是哪四种？
             mainThreadSupport = builder.getMainThreadSupport();
             mainThreadPoster = mainThreadSupport != null ? mainThreadSupport.createPoster(this) : null;
             backgroundPoster = new BackgroundPoster(this);
             asyncPoster = new AsyncPoster(this);

            //记录事件总数
             indexCount = builder.subscriberInfoIndexes != null ? builder.subscriberInfoIndexes.size() : 0;
            //查找添加@subscribe注解的方法
             subscriberMethodFinder = new SubscriberMethodFinder(builder.subscriberInfoIndexes,
             builder.strictMethodVerification, builder.ignoreGeneratedIndex);

            //处理事件方法异常时是否需要打印log default true
             logSubscriberExceptions = builder.logSubscriberExceptions;
            //没有订阅者订阅此消息时是否打印log default true
             logNoSubscriberMessages = builder.logNoSubscriberMessages;
            //处理方法有异常，是否发送这个event default true
             sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
            //没有处理方法时，是否需要发送sendNoSubscriberEvent这个标签 default true
             sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
            //是否需要发送 throwSubscriberException 标签 default true
             throwSubscriberException = builder.throwSubscriberException;
            //与接收者有继承关系的类是否都需要发送 default true
             eventInheritance = builder.eventInheritance;
            //线程池
             executorService = builder.executorService;
             }
     *
     *
     *
     *   register(this)方法：
     *
     *       public void register(Object subscriber) {
     *       //获取注册对象的class对象信息
             Class<?> subscriberClass = subscriber.getClass();
            //获取class对象的订阅方法，添加@Subscribe 注解的方法
             List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);    待分析~~~~~~~~~~~
             synchronized (this) {
             for (SubscriberMethod subscriberMethod : subscriberMethods) {
            //对添加@Subscribe 注解的方法进行注册
             subscribe(subscriber, subscriberMethod);
             }
             }
             }
     *
     *  SubscriberMethod 的类主要是对获取到的@Subscribe方法进行封装
     *  封装了一些线程、是否是粘性事件等一些信息
     *  public class SubscriberMethod {
                 final Method method;
                 final ThreadMode threadMode;
                 final Class<?> eventType;
                 final int priority;
                 final boolean sticky;
         }
     *
     *
     *
     *  findSubscriberMethods() 方法获取该订阅者的订阅方法
     *
     *
     *    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
             List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
             if (subscriberMethods != null) {//若缓存中有则直接从缓存中获取
             return subscriberMethods;
             }
            //缓存中没有则直接通过findUsingReflection() 方法 或者 findUsingInfo() 方法获取订阅方法
             if (ignoreGeneratedIndex) {// 该参数表示是否忽略注解器生成的MyEventBusIndex，默认为false  （是否强制使用反射，即使生成了索引）
             subscriberMethods = findUsingReflection(subscriberClass);
             } else {
             subscriberMethods = findUsingInfo(subscriberClass);
             }
             if (subscriberMethods.isEmpty()) {
             throw new EventBusException("Subscriber " + subscriberClass
             + " and its super classes have no public methods with the @Subscribe annotation");
             } else {
            //获取之后放入缓存中
             METHOD_CACHE.put(subscriberClass, subscriberMethods);
             return subscriberMethods;
             }
             }
     *      1...通过findUsingInfo()方法获取订阅方法
     *
     *        private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
     *          //通过FindState对象存储找到的方法信息
                 FindState findState = prepareFindState();
                 findState.initForSubscriber(subscriberClass);
                 while (findState.clazz != null) { //while循环获取订阅的方法信息，包括父类
                // 获取订阅者信息
                 findState.subscriberInfo = getSubscriberInfo(findState);
                 if (findState.subscriberInfo != null) { //订阅者信息不为空
                 SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                 for (SubscriberMethod subscriberMethod : array) {
                 if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                 findState.subscriberMethods.add(subscriberMethod);
                 }
                 }
                 } else {
                    //订阅者信息为空则通过反射再次获取一次
                 findUsingReflectionInSingleClass(findState);
                 }
                //findState.clazz 设置为父类，递归获取父类订阅的方法信息
                 findState.moveToSuperclass();
                 }
                 return getMethodsAndRelease(findState);
              }

            2...通过findUsingReflection()方法获取订阅方法：

     *          private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
     *          //通过FindState对象存储找到的方法信息
                 FindState findState = prepareFindState();

                 findState.initForSubscriber(subscriberClass);
                //while循环获取订阅的方法信息，包括父类
                while (findState.clazz != null) {
                 findUsingReflectionInSingleClass(findState);
                //findState.clazz 设置为父类，递归获取父类订阅的方法信息
                 findState.moveToSuperclass();
                 }
                 return getMethodsAndRelease(findState);
                 }



            findUsingReflectionInSingleClass() 此方法是通过反射从订阅者中获取订阅方法

     *
     *      private void findUsingReflectionInSingleClass(FindState findState) {
             Method[] methods;
             try {
             // This is faster than getMethods, especially when subscribers are fat classes like Activities
             methods = findState.clazz.getDeclaredMethods();//反射获取该类中声明的所有方法
             } catch (Throwable th) {
             // Workaround for java.lang.NoClassDefFoundError, see https://github.com/greenrobot/EventBus/issues/149
             methods = findState.clazz.getMethods();
             findState.skipSuperClasses = true;
             }
     //遍历所有的方法并且找到符合要求的方法，并且封装成SubscriberMethod 保存在findState.subscriberMethods 这个list中
             for (Method method : methods) {
             int modifiers = method.getModifiers();
            //校验方法的修饰符，修饰符必须是public 非static 非abstract
             if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                //校验输入参数
             Class<?>[] parameterTypes = method.getParameterTypes();
             if (parameterTypes.length == 1) {//注解方法必须有一个有效参数
             Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
             if (subscribeAnnotation != null) {
             Class<?> eventType = parameterTypes[0];
             if (findState.checkAdd(method, eventType)) {
             ThreadMode threadMode = subscribeAnnotation.threadMode();
            //将校验完成的方法放入findState.subscriberMethods的list中保存
             findState.subscriberMethods.add(new SubscriberMethod(method, eventType, threadMode,
             subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
             }
             }
             } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
             String methodName = method.getDeclaringClass().getName() + "." + method.getName();
             throw new EventBusException("@Subscribe method " + methodName +
             "must have exactly 1 parameter but has " + parameterTypes.length);
             }
             } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
             String methodName = method.getDeclaringClass().getName() + "." + method.getName();
             throw new EventBusException(methodName +
             " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
             }
             }
             }

             public void register(Object subscriber) {
             Class<?> subscriberClass = subscriber.getClass();
             List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
             synchronized (this) {
                //获取到订阅方法的列表后，依次建立订阅关系
             for (SubscriberMethod subscriberMethod : subscriberMethods) {
             subscribe(subscriber, subscriberMethod);
             }
             }
             }

                subscribe() 方法

     *
     *      subscribe(subscriber, subscriberMethod); 具体如何注册的逻辑如下
     *
     *        private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
                 Class<?> eventType = subscriberMethod.eventType;
                //将订阅者和订阅方法封装成一个Subscription对象
                 Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
                //根据事件类型从缓存中取出subscriptions（map）
                 CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
                 if (subscriptions == null) {//还未被注册
                 subscriptions = new CopyOnWriteArrayList<>();
                 subscriptionsByEventType.put(eventType, subscriptions);
                 } else {
                 if (subscriptions.contains(newSubscription)) {
                 throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                 + eventType);
                 }
                 }
                    //将新方法在subscriptions尾部追加
                 int size = subscriptions.size();
                 for (int i = 0; i <= size; i++) {
                 if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                 subscriptions.add(i, newSubscription);
                 break;
                 }
                 }

                 List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
                 if (subscribedEvents == null) {//subscribedEvents为空 然后new 一个 subscribedEvents 并且put进 typesBySubscriber 中
                 subscribedEvents = new ArrayList<>();
                 typesBySubscriber.put(subscriber, subscribedEvents);
                 }
                 subscribedEvents.add(eventType);//subscribedEvents集合将订阅方法全部add进去，此时typesBySubscriber这个map中就保存了订阅者subscriber（注册时传入的this）和订阅方法（添加@Subscribe的方法）的所有class类型


                //如果是粘性事件的话需要做如下处理  粘性事件 就是在发送事件之后再订阅该事件也能收到该事件
                //如果是粘性消息则将缓存中的消息发送给订阅
                 if (subscriberMethod.sticky) {
                 if (eventInheritance) {
                 // Existing sticky events of all subclasses of eventType have to be considered.
                 // Note: Iterating over all events may be inefficient with lots of sticky events,
                 // thus data structure should be changed to allow a more efficient lookup
                 // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                 Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                 for (Map.Entry<Class<?>, Object> entry : entries) {
                 Class<?> candidateEventType = entry.getKey();
                 if (eventType.isAssignableFrom(candidateEventType)) {
                 Object stickyEvent = entry.getValue();
                 checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                 }
                 }
                 } else {
                 Object stickyEvent = stickyEvents.get(eventType);
                 checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                 }
                 }
                 }
     *
     *
     *      EventBus.getDefault().post("eventBus");
     *      EventBus.getDefault() 获取eventBus单例对象
     *      post() 方法源码
     *
     *
                public void post(Object event) {
                //currentPostingThreadState 是一个ThreadLocal对象，保存了当前线程的PostingThreadState对象
                    PostingThreadState postingState = currentPostingThreadState.get();
                //eventQueue为postingState中保存的一个list
                    List<Object> eventQueue = postingState.eventQueue;
                    eventQueue.add(event);
                        //是否处于posting状态
                    if (!postingState.isPosting) {
                        postingState.isMainThread = isMainThread();//是否在主线程中执行
                        postingState.isPosting = true;
                        if (postingState.canceled) {//posting过程被取消
                            throw new EventBusException("Internal error. Abort state was not reset");
                        }
                        try {
                            while (!eventQueue.isEmpty()) {
                                //eventQueue中的所有消息依次发送
                                postSingleEvent(eventQueue.remove(0), postingState);
                            }
                        } finally {
                            //发送结束状态重置
                            postingState.isPosting = false;
                            postingState.isMainThread = false;
                        }
                    }
                }
     *                    postSingleEvent(eventQueue.remove(0), postingState); 方法
     *
     *      private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
     *      //获取当前发送的事件的class对象
                 Class<?> eventClass = event.getClass();
                 boolean subscriptionFound = false;
                 if (eventInheritance) {//此变量标识 是否发送当前类的superClass的事件
                 List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
                 int countTypes = eventTypes.size();
                 for (int h = 0; h < countTypes; h++) {
                 Class<?> clazz = eventTypes.get(h);
                 subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
                 }
                 } else {
                 subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
                 }
                 if (!subscriptionFound) {
                 if (logNoSubscriberMessages) {
                 logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
                 }
                 if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                 eventClass != SubscriberExceptionEvent.class) {
                 post(new NoSubscriberEvent(this, event));
                    }
             }
        }
     *        Looks up all Class objects including super classes and interfaces. Should also work for interfaces.
     *        //找出所有的类对象包括超类和接口
                    private static List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
                        synchronized (eventTypesCache) {
                            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
                            if (eventTypes == null) {
                                eventTypes = new ArrayList<>();
                                Class<?> clazz = eventClass;
                                while (clazz != null) {
                                    eventTypes.add(clazz);
                                    addInterfaces(eventTypes, clazz.getInterfaces());
                                    clazz = clazz.getSuperclass();
                                }
                                eventTypesCache.put(eventClass, eventTypes);
                            }
                            return eventTypes;
                        }
                    }
     *
     *
     *
     *          private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
                             CopyOnWriteArrayList<Subscription> subscriptions;
                             synchronized (this) {
                            //Subscription 这个对象的作用
                             subscriptions = subscriptionsByEventType.get(eventClass);
                             }
                             if (subscriptions != null && !subscriptions.isEmpty()) {
                             for (Subscription subscription : subscriptions) {
                             postingState.event = event;
                             postingState.subscription = subscription;
                             boolean aborted = false;
                             try {
                             postToSubscription(subscription, event, postingState.isMainThread);
                             aborted = postingState.canceled;
                             } finally {
                             postingState.event = null;
                             postingState.subscription = null;
                             postingState.canceled = false;
                             }
                             if (aborted) {
                             break;
                             }
                             }
                             return true;
                             }
                             return false;
                             }
     *
     *
     *
     *      //根据注解选择执行的线程类型
     *       private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
                     switch (subscription.subscriberMethod.threadMode) {
                     case POSTING:
                     invokeSubscriber(subscription, event);
                     break;
                     case MAIN:
                     if (isMainThread) {
                     invokeSubscriber(subscription, event);
                     } else {
                     mainThreadPoster.enqueue(subscription, event);
                     }
                     break;
                     case MAIN_ORDERED:
                     if (mainThreadPoster != null) {
                     mainThreadPoster.enqueue(subscription, event);
                     } else {
                     // temporary: technically not correct as poster not decoupled from subscriber
                     invokeSubscriber(subscription, event);
                     }
                     break;
                     case BACKGROUND:
                     if (isMainThread) {
                     backgroundPoster.enqueue(subscription, event);
                     } else {
                     invokeSubscriber(subscription, event);
                     }
                     break;
                     case ASYNC:
                     asyncPoster.enqueue(subscription, event);
                     break;
                     default:
                     throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
                     }
                     }
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */
}
