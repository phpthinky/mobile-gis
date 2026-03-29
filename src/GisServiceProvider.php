<?php

namespace Native\Mobile\Providers;

use Illuminate\Support\ServiceProvider;
use Native\Mobile\Gis;

class GisServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        $this->app->singleton(Gis::class, function () {
            return new Gis;
        });
    }
}
