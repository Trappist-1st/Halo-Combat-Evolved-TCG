package com.haloce.tcg.test;

import com.haloce.tcg.net.NetResponse;
import com.haloce.tcg.net.NetworkGameClient;

import java.util.List;
import java.util.Map;

/**
 * 网络游戏测试类
 * 演示客户端连接、房间操作和游戏命令
 */
public class NetworkGameTest {
    public static void main(String[] args) {
        System.out.println("=== Halo CE TCG 网络测试 ===");

        try {
            // 1. 连接到服务器
            NetworkGameClient client1 = new NetworkGameClient("localhost", 19110);
            NetworkGameClient client2 = new NetworkGameClient("localhost", 19110);

            System.out.println("✓ 两个客户端已连接到服务器");

            // 2. 列出房间
            NetResponse roomsResponse = client1.listRooms();
            System.out.println("✓ 房间列表: " + roomsResponse.data());

            // 3. 创建房间
            NetResponse createResponse = client1.createRoom("test-room", "DUEL_1V1",
                List.of("P1", "P2"), Map.of());
            System.out.println("✓ 创建房间: " + createResponse.data());

            // 4. 加入房间
            NetResponse join1 = client1.joinRoom("test-room", "P1");
            NetResponse join2 = client2.joinRoom("test-room", "P2");
            System.out.println("✓ P1 加入房间: " + join1.data());
            System.out.println("✓ P2 加入房间: " + join2.data());

            // 5. 获取游戏状态
            NetResponse state = client1.state();
            System.out.println("✓ 游戏状态: " + state.data());

            // 6. 演示一些游戏命令
            // 注意：这里只是演示命令格式，实际需要根据手牌和战场状态调整

            System.out.println("\n=== 命令示例 ===");
            System.out.println("部署命令: {\"type\":\"DEPLOY\", \"payload\":{\"cardInstanceId\":\"<手牌ID>\", \"lane\":\"ALPHA\", \"row\":\"FRONTLINE\"}}");
            System.out.println("攻击命令: {\"type\":\"ATTACK\", \"payload\":{\"attackerInstanceId\":\"<攻击者ID>\", \"defenderInstanceId\":\"<防御者ID>\"}}");
            System.out.println("劫持命令: {\"type\":\"HIJACK\", \"payload\":{\"hijackerInstanceId\":\"<劫持者ID>\", \"targetVehicleInstanceId\":\"<目标载具ID>\"}}");
            System.out.println("结束回合: {\"type\":\"END_TURN\", \"payload\":{}}");

            // 7. 离开房间
            NetResponse leave1 = client1.leaveRoom();
            NetResponse leave2 = client2.leaveRoom();
            System.out.println("✓ P1 离开房间: " + leave1.data());
            System.out.println("✓ P2 离开房间: " + leave2.data());

            // 8. 关闭连接
            client1.close();
            client2.close();
            System.out.println("✓ 连接已关闭");

        } catch (Exception e) {
            System.out.println("网络测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}