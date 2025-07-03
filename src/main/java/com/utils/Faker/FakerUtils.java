package com.utils.Faker;

import com.github.javafaker.Faker;

public class FakerUtils {

    public static void main(String[] args) {
        Faker faker=new Faker();
        System.out.println(faker.address().cityName());
    }


}
