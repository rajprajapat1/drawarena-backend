package com.drawarena.drawarena_backend.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class TestConteroller {

    @GetMapping("/test")
    public String test() {
        return "DrawArena is running";
    }

}
