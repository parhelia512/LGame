#pragma once
#ifndef LOON_SOCKET
#define LOON_SOCKET
#include "SDLSupport.h"

// Windows / Xbox 平台
#if defined(_WIN32) || defined(_WIN64) || defined(_XBOX)
    #define WIN32_LEAN_AND_MEAN
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #include <winhttp.h>
    #include <iphlpapi.h>
    #pragma comment(lib, "winhttp.lib")
    #pragma comment(lib, "ws2_32.lib")
    #pragma comment(lib, "iphlpapi.lib")
    typedef SOCKET socket_t;
#else 
    #include <unistd.h>
    #include <string.h>
    #include <stdlib.h>
// PlayStation 平台 (PS4/PS5/Vita)
#if defined(__ORBIS__) || defined(__PROSPERO__) || defined(__VITA__)
    #include <net.h>
    #include <sys/socket.h>
    #include <arpa/inet.h>
// PSP 平台
#elif defined(__PSP__)
    #include <pspnet.h>
    #include <pspnet_inet.h>
    #include <sys/socket.h>
    #include <arpa/inet.h>
// 其它 POSIX 平台
#else
    #include <arpa/inet.h>
    #include <sys/types.h>
    #include <sys/socket.h>
#endif
// Nintendo 平台
#if defined(__SWITCH__)
    #include <switch/services/socket.h>
    #include <switch/types.h>
#elif defined(__3DS__)
    #include <3ds/soc.h>
#elif defined(__WIIU__)
    #include <wiiu/soc.h>
#endif
// 通用 POSIX 网络库
    #include <curl/curl.h>
    #include <netinet/in.h>
    #include <fcntl.h>
    #include <netdb.h>
    #include <ifaddrs.h>
    #include <errno.h>
    typedef int socket_t;
#endif // 平台分支结束

#define BUFFER_SIZE 1024

#ifdef __cplusplus
extern "C" {
#endif

// 错误码枚举
typedef enum {
  SOCKET_OK = 0,
  SOCKET_ERR_PARAM = -1,
  SOCKET_ERR_INIT = -2,
  SOCKET_ERR_CONNECT = -3,
  SOCKET_ERR_SEND = -4,
  SOCKET_ERR_RECV = -5,
  SOCKET_ERR_TIMEOUT = -6,
  SOCKET_ERR_NOMEM = -7,
  SOCKET_ERR_UNKNOWN = -99
} SocketErrorCode;

void ImportSocketInclude();

int64_t GetURLFileSize(const char* url);

void DownloadURL(const char* url,uint8_t* outbytes, int32_t len);

int Load_Socket_Init();

void Load_Socket_Free();

void Load_Socket_Close(socket_t sock);

socket_t Load_Connect_Server(int port);

socket_t Load_Connect_Client(const char* ip, int port);

socket_t Load_Create_LinkServerToClient(socket_t server_fd);

int Load_Socket_Send(socket_t sock, const uint8_t* msg, const int flags);

int Load_Socket_Recv(socket_t sock, uint8_t* buf, int bufsize);

void Load_Socket_FirstIP(char* out_ip, int out_size, int prefer_ipv6);

int Load_Socket_Timeout(socket_t sock, int recv_timeout_ms, int send_timeout_ms);

#ifdef __cplusplus
}
#endif

#endif
