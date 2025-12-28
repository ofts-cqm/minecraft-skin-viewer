# minecraft-skin-viewer

中文翻译位于下面

This repository is a minecraft skin animation generator, and also the host program of a website using it. It was initially a fork from 404E's [Minecraft Skin Viewer](https://github.com/4o4E/minecraft-skin-viewer). 

Upon Aayong's request, I created a website featuring skin-animation generation, using the upstream repository mentioned above as the API source. 

However, due to the complexity of Aayong's requirement, I forked this repository to add some new generation options to adopt Aayong's tasks. I also packaged the website together and used this program to serve the static webpage. 

Therefore, it is no longer the same as the original repository. 

-----

English Translations are Above

这个仓库是一个我的世界皮肤动画生成器，以及一个网站的host。它原本是404E的[Minecraft Skin Viewer](https://github.com/4o4E/minecraft-skin-viewer)的一个fork。

根据Aayong的要求，我制作了一个网站，用于在线创作各种皮肤动图，并使用以上的仓库作为API。

然而，因为Aayong要求的复杂性，我fork了以上仓库并添加了新的生成项目，来完成Aayong的目标。我还将我制作的网站一并打包了进去，并使用这个程序serve static我的网站

因此，这个仓库已经和原本的不一样了

## http服务

[http-server模块](http-server)提供了一个简单的http server用于根据需求生成皮肤渲染图

### **:warning:注意:warning:**

1. 此http服务使用mysql来储存玩家`uuid`和`name`, 请在部署后配置db.properties, 默认使用名为`skin`的数据库
2. `config.yml`可以设置http服务的地址和端口, 以及代理(用于从mojang的服务器获取玩家数据, 没有可以留空)
3. 对于linux, 此应用需要`xserver`, 你可以使用`gnome`/`kde`/`xvfb`, 另外我自己在`ubuntu`上使用的时候, 需要添加`-Dprism.forceGPU=true`以及`-Djdk.gtk.version=3`
4. 您可能需要在db.properties的jdbcurl中加入allowPublicKeyRetrieval=true参数来使用mysql server

### 使用

1. 安装11或更高版本的[java](https://adoptium.net/)以及[mysql](https://downloads.mysql.com/archives/community/)
2. 从[release](https://github.com/4o4E/minecraft-skin-viewer/releases/latest)下载对应操作系统的jar文件
3. 在控制台中使用`java -jar http-server-${plateform}.jar`启动服务
4. 根据“api接口”章节的内容来使用该API，或者：
5. 打开https:localhost:2345 （默认）来使用网站

### 示例配置文件

```yaml
# http服务绑定地址
address: "127.0.0.1"
# http服务绑定端口
port: 80
# 请求mojang服务器时使用的代理, 设置为null则不使用代理
proxy:
  # 代理地址
  address: localhost
  # 代理端口
  port: 7890
# 缓存超时时长(超时后获取时将重新从mojang服务器获取, 包括uuid对应的用户名和皮肤, 服务不会主动移除过期缓存, 仅在获取时检测超时)
timeout: 86400
```

### api接口

#### 获取渲染图

url: `/render/{type}/{content}/{position}`

| url参数    | 含义        | 示例                                       |
|----------|-----------|------------------------------------------|
| type     | 以何种方式指定玩家 | `name`/`id`                              |
| content  | 指定玩家的内容   | `玩家名`/`uuid`                             |
| position | 生成的模式     | `sneak`/`hip`/`sk`/`dsk`/`head`/`dhead`/`homo`/`temple`/`trump`/`litang` |

**获取渲染图时不同模式的可用参数(打勾的参数意味支持get参数设置)**

| 请求参数     | 含义              | 备注             | sneak              | sk                 | dsk                | head               | dhead              | homo               |
|----------|-----------------|----------------|--------------------|--------------------|--------------------|--------------------|--------------------|--------------------|
| bg       | 背景颜色            | 默认值`#1F1B1D`   | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: |
| light    | 环境光颜色           | 默认值空           | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: |
| head     | 头大小             | 默认值`1.0`浮点数    | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: |                    |                    | :heavy_check_mark: |
| x        | 旋转时的速度          | 默认值`20`        |                    |                    | :heavy_check_mark: |                    | :heavy_check_mark: |                    |
| y        | 俯仰角             | 默认值`20`        |                    |                    | :heavy_check_mark: |                    | :heavy_check_mark: |                    |
| slim     | 是否使用alex模型      | 默认值空(跟随mc皮肤设置) | :heavy_check_mark: | :heavy_check_mark: | :heavy_check_mark: |                    |                    | :heavy_check_mark: |
| duration | gif的帧持续时长, 单位ms | 默认值`40`        | :heavy_check_mark: |                    | :heavy_check_mark: |                    | :heavy_check_mark: |                    |

**duration的精度为10ms(gif仅支持此精度)**

**示例请求**

```http request
GET http://localhost:2345/render/name/404E/sk?head=1.5
```

#### 刷新皮肤缓存

url: `/refresh/{type}/{content}`

| url参数   | 含义        | 示例           |
|---------|-----------|--------------|
| type    | 以何种方式指定玩家 | `name`/`id`  |
| content | 指定玩家的内容   | `玩家名`/`uuid` |

**示例请求**

```http request
GET http://localhost:2345/refresh/name/404E
```

#### 获取原始数据

url: `/data/{type}/{content}`

| url参数   | 含义        | 示例           |
|---------|-----------|--------------|
| type    | 以何种方式指定玩家 | `name`/`id`  |
| content | 指定玩家的内容   | `玩家名`/`uuid` |

**示例请求**

```http request
GET http://localhost:2345/data/name/404E
```

**示例响应**
```json
{"uuid":"22df77dd37b0414b8f1e3c7d2585fc79","name":"404E","slim":true,"update":1683961680455,"hash":"4daa024bc2d35de2b26025051817d04491ad586e5a2ab85f9dad608b009ac7d"}
```

## Docker 部署

支持使用 Docker 进行部署, 镜像不包含mysql, [compose部署参考](http-server-linux/docker-compose.yml)


