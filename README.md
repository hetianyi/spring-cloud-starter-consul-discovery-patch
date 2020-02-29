# spring-cloud-consul-discovery-patch
spring-cloud-consul-discovery模块补丁，修复了无法重新注册服务的问题。

#### 使用场景

使用consul注册中心的spring cloud应用，使用spring cloud提供的客户端模块：
```spring-cloud-starter-consul-discovery```，
此模块在以下情况（不仅仅限于以下情况）下无法重新注册服务：
- 当consul agent和spring boot应用都没问题，但是网络出现了问题，
spring boot应用无法向consul agent发送心跳，或者consul无法对spring boot应用进行健康检查，
在一定时间后spring boot应用被consul agent会自动剔除。当网络再次恢复，spring boot应用无法重新注册到consul agent。

#### 使用方法

```xml
<dependency>
    <groupId>com.github.hetianyi</groupId>
    <artifactId>spring-cloud-starter-consul-discovery-patch</artifactId>
    <version>1.0.0</version>
</dependency>
```
> 注意：使用此补丁必须设置```spring.cloud.consul.discovery.heartbeat.enabled=true```
