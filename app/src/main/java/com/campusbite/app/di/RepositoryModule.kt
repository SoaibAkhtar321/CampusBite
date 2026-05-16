package com.campusbite.app.di

import com.campusbite.app.data.repository.MenuRepository
import com.campusbite.app.data.repository.MenuRepositoryImpl
import com.campusbite.app.data.repository.UserRepository
import com.campusbite.app.data.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindMenuRepository(
        impl: MenuRepositoryImpl
    ): MenuRepository
}
