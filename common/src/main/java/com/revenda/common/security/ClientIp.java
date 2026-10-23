package com.revenda.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * A rede de onde a chamada vem: o IPv4 inteiro ou o prefixo /64 do IPv6, que o assinante troca quando quer.
 * Só vale o endereço da conexão; atrás de proxy, quem o corrige é o servidor, com os proxies confiáveis de
 * {@code server.forward-headers-strategy} — nenhum cabeçalho do cliente é lido aqui.
 */
public final class ClientIp {

    private static final int ADDRESS_MAX = 45;
    private static final int IPV6_BYTES = 16;
    private static final int IPV6_NETWORK_BYTES = 8;

    private ClientIp() {
    }

    public static String networkOf(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip == null) {
            return "";
        }
        if (ip.indexOf(':') >= 0 && ip.length() <= ADDRESS_MAX) {
            try {
                byte[] address = InetAddress.getByName(ip).getAddress();
                if (address.length == IPV6_BYTES) {
                    return HexFormat.of().formatHex(address, 0, IPV6_NETWORK_BYTES);
                }
            } catch (UnknownHostException e) {
                return ip;
            }
        }
        return ip.length() <= ADDRESS_MAX ? ip : ip.substring(0, ADDRESS_MAX);
    }
}
