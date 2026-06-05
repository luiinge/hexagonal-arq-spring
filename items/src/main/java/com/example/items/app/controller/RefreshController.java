package com.example.items.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class RefreshController {

    @Autowired
    private Environment environment;

    // esta anotación es necesaria para que el endpoint de refresh 
    // funcione, ya que le indica a Spring que esta clase es un bean 
    // que puede ser refrescado en caliente, es decir, que puede ser
    //  re-instanciado con la nueva configuración sin necesidad de reiniciar el servicio.
    @RefreshScope 
    @GetMapping("/get-config")
    public String getConfig() {
        String serverPort = environment.getProperty("server.port");
        return "Server Port: " + serverPort;
    }
}
