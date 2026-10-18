import React from 'react';

export const ProductSkeleton: React.FC = () => {
  return (
    <div className="bg-white rounded-2xl border border-brand-border p-4 flex flex-col justify-between animate-pulse">
      <div className="flex justify-between items-center mb-3">
        <div className="w-16 h-5 bg-gray-200 rounded-lg" />
        <div className="w-20 h-5 bg-gray-200 rounded-md" />
      </div>
      <div className="w-full h-36 bg-gray-200 rounded-xl mb-4" />
      <div className="space-y-2 mb-4">
        <div className="w-3/4 h-5 bg-gray-200 rounded" />
        <div className="w-1/2 h-3 bg-gray-200 rounded" />
      </div>
      <div className="pt-3 border-t border-brand-border/40 flex justify-between items-center">
        <div className="w-20 h-6 bg-gray-200 rounded" />
        <div className="w-24 h-10 bg-gray-200 rounded-xl" />
      </div>
    </div>
  );
};

export const CatalogSkeleton: React.FC = () => {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-6">
      <ProductSkeleton />
      <ProductSkeleton />
      <ProductSkeleton />
    </div>
  );
};
