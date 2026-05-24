package com.aoaojiao.catmq.nameserver.client;

import com.aoaojiao.catmq.nameserver.protocol.*;
import io.netty.channel.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * NameServerClient 单元测试
 *
 * @author DD
 */
public class NameServerClientTest {

    private NameServerClient client;
    private Channel mockChannel;
    private ChannelFuture mockChannelFuture;

    @Before
    public void setUp() throws Exception {
        client = new NameServerClient("localhost:9999");
        mockChannel = mock(Channel.class);
        mockChannelFuture = mock(ChannelFuture.class);

        // 通过反射注入 mock channel
        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        // 配置 mock channel 行为
        when(mockChannel.writeAndFlush(any())).thenReturn(mockChannelFuture);
        when(mockChannelFuture.isSuccess()).thenReturn(true);
    }

    @After
    public void tearDown() {
        // 清理资源
        try {
            Field groupField = NameServerClient.class.getDeclaredField("group");
            groupField.setAccessible(true);
            Object group = groupField.get(client);
            if (group != null) {
                // 不需要真正关闭 mock
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void testConstructor() {
        NameServerClient c = new NameServerClient("192.168.1.1:8080");
        assertThat(c).isNotNull();
    }

    @Test
    public void testConstructorWithLocalhost() {
        NameServerClient c = new NameServerClient("localhost:8888");
        assertThat(c).isNotNull();
    }

    @Test
    public void testRegisterBrokerSuccess() throws Exception {
        BrokerInfo brokerInfo = new BrokerInfo();
        brokerInfo.setBrokerName("test-broker");
        brokerInfo.setBrokerIp("127.0.0.1");
        brokerInfo.setBrokerPort(8888);
        brokerInfo.setBrokerId(1);
        brokerInfo.setClusterName("default-cluster");
        brokerInfo.setTopicList(new String[]{"topic1"});

        // 模拟连接已建立
        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        boolean result = client.registerBroker(brokerInfo);
        assertThat(result).isFalse(); // 因为没有 mock response 返回，future 不会完成

        // 验证 writeAndFlush 被调用
        verify(mockChannel, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    public void testSendHeartBeat() throws Exception {
        String brokerName = "heartbeat-broker";
        String[] topicList = {"topic1", "topic2"};

        // 模拟连接已建立
        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        boolean result = client.sendHeartBeat(brokerName, topicList);
        assertThat(result).isFalse(); // 因为没有 mock response 返回

        verify(mockChannel, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    public void testGetTopicRoute() throws Exception {
        String topic = "order_topic";

        // 模拟连接已建立
        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        List<BrokerInfo> result = client.getTopicRoute(topic);
        assertThat(result).isNull(); // 因为没有 mock response 返回

        verify(mockChannel, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    public void testClose() throws Exception {
        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        client.close();

        verify(mockChannel).close();
    }

    @Test
    public void testCloseWithNullChannel() {
        // 确保 null channel 不会导致异常
        NameServerClient c = new NameServerClient("localhost:9999");
        c.close(); // 应该安全退出
    }

    @Test
    public void testRegisterBrokerWithNullTopicList() throws Exception {
        BrokerInfo brokerInfo = new BrokerInfo();
        brokerInfo.setBrokerName("test-broker");
        brokerInfo.setBrokerIp("127.0.0.1");
        brokerInfo.setBrokerPort(8888);
        brokerInfo.setBrokerId(1);
        brokerInfo.setClusterName("default-cluster");
        brokerInfo.setTopicList(null);

        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        boolean result = client.registerBroker(brokerInfo);
        assertThat(result).isFalse();

        verify(mockChannel, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    public void testSendHeartBeatWithEmptyTopicList() throws Exception {
        String brokerName = "empty-broker";
        String[] topicList = new String[]{};

        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        boolean result = client.sendHeartBeat(brokerName, topicList);
        assertThat(result).isFalse();

        verify(mockChannel, atLeastOnce()).writeAndFlush(any());
    }

    @Test
    public void testGetTopicRouteWithSpecialCharacters() throws Exception {
        String topic = "order_pay_topic-test_123";

        Field channelField = NameServerClient.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        channelField.set(client, mockChannel);

        List<BrokerInfo> result = client.getTopicRoute(topic);
        assertThat(result).isNull();

        verify(mockChannel, atLeastOnce()).writeAndFlush(any());
    }
}
