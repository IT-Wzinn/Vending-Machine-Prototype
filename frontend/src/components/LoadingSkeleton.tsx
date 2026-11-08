import React from 'react';

export const ProductSkeleton: React.FC = () => {
  return (
    <div className="glass-card rounded-3xl p-4 flex flex-col justify-between animate-pulse border border-emerald-500/15">
      <div className="flex justify-between items-center mb-3">
        <div className="w-16 h-5 bg-emerald-950/10 rounded-lg" />
        <div className="w-20 h-5 bg-emerald-950/10 rounded-md" />
      </div>
      <div className="w-full h-36 bg-emerald-950/10 rounded-2xl mb-4" />
      <div className="space-y-2 mb-4">
        <div className="w-3/4 h-5 bg-emerald-950/10 rounded-lg" />
        <div className="w-1/2 h-3 bg-emerald-950/10 rounded-md" />
      </div>
      <div className="pt-3 border-t border-emerald-500/15 flex justify-between items-center">
        <div className="w-20 h-6 bg-emerald-950/10 rounded-md" />
        <div className="w-24 h-10 bg-emerald-950/15 rounded-2xl" />
      </div>
    </div>
  );
};

export const CatalogSkeleton: React.FC = () => {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6">
      <ProductSkeleton />
      <ProductSkeleton />
      <ProductSkeleton />
    </div>
  );
};
