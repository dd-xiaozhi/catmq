package com.aoaojiao.catmq.nameserver;

import com.aoaojiao.catmq.nameserver.config.NameServerConfig;
import com.aoaojiao.catmq.nameserver.server.NameServer;
import com.aoaojiao.catmq.nameserver.service.HeartBeatService;
import com.aoaojiao.catmq.nameserver.service.RouteInfoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NameServer 启动类
 *
 * @author DD
 */
public class NameServerStartup {

    private static final Logger log = LoggerFactory.getLogger(NameServerStartup.class);

    public static void main(String[] args) {
        try {
            // 1. 加载配置
            NameServerConfig config = loadConfig(args);

            log.info("NameServer configuration loaded:");
            log.info("  Server port: {}", config.getServerPort());
            log.info("  HeartBeat timeout: {}ms", config.getHeartBeatTimeoutMs());
            log.info("  Registry file: {}", config.getBrokerRegistryPath());

            // 2. 初始化核心组件
            RouteInfoManager routeInfoManager = new RouteInfoManager(config);
            HeartBeatService heartBeatService = new HeartBeatService(routeInfoManager, config);
            NameServer nameServer = new NameServer(config, routeInfoManager, heartBeatService);

            // 3. 启动 NameServer
            nameServer.start();

        } catch (Exception e) {
            log.error("NameServer startup failed", e);
            System.exit(1);
        }
    }

    /**
     * 加载配置
     * 支持通过命令行参数覆盖默认配置
     *
     * @param args 命令行参数
     * @return NameServer 配置
     */
    private static NameServerConfig loadConfig(String[] args) {
        NameServerConfig config = new NameServerConfig();

        // 解析命令行参数
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                if ("-p".equals(arg) || "--port".equals(arg)) {
                    if (i + 1 < args.length) {
                        config.setServerPort(Integer.parseInt(args[++i]));
                    }
                } else if ("-t".equals(arg) || "--timeout".equals(arg)) {
                    if (i + 1 < args.length) {
                        config.setHeartBeatTimeoutMs(Long.parseLong(args[++i]));
                    }
                } else if ("-h".equals(arg) || "--help".equals(arg)) {
                    printUsage();
                    System.exit(0);
                }
            }
        }

        return config;
    }

    /**
     * 打印使用帮助
     */
    private static void printUsage() {
        System.out.println("Usage: java -jar catmq-nameserver.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -p, --port <port>           NameServer listen port (default: 9876)");
        System.out.println("  -t, --timeout <ms>          HeartBeat timeout in milliseconds (default: 30000)");
        System.out.println("  -h, --help                  Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar catmq-nameserver.jar");
        System.out.println("  java -jar catmq-nameserver.jar -p 9876");
        System.out.println("  java -jar catmq-nameserver.jar -p 9876 -t 60000");
    }
}