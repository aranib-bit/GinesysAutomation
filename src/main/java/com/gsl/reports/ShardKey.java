package com.gsl.reports;

import com.gsl.enums.AppOperation;
import com.gsl.enums.ISubModule;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;

public record ShardKey(MainModuleType module, ISubModule subModule, AppOperation operation) {}
